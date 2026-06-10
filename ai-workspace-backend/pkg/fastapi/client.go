// Package fastapi 提供与 Python FastAPI AI 服务通信的 HTTP 客户端。
// 对应 Spring Boot workspace-framework 的 FastApiClient，封装三种调用模式：
//   - PostForData: 一次性 JSON 请求（Agent/Workflow 运行）
//   - Send:        带超时的 fire-and-forget（嵌入构建/删除）
//   - Stream:      SSE 逐行回调（Chat/RAG 流式代理）
package fastapi

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/aiworkspace/backend/internal/config"
)

// standardResp 是 FastAPI 统一响应结构，与 Python 侧 utils/response.py 对应
type standardResp struct {
	Code    int             `json:"code"`
	Message string          `json:"message"`
	Data    json.RawMessage `json:"data"`
}

// Client 是全局 FastAPI 客户端单例
var Client *fastapiClient

type fastapiClient struct {
	baseURL    string
	httpClient *http.Client
	// SSE 流使用更长的超时，对应 Spring Boot streamTimeout 180s
	streamClient *http.Client
}

// Init 根据配置初始化客户端单例，复用连接池减少 TCP 握手开销。
func Init(cfg config.FastAPIConfig) {
	transport := &http.Transport{
		MaxIdleConns:        100,
		MaxIdleConnsPerHost: 20,
		IdleConnTimeout:     90 * time.Second,
	}
	timeout := time.Duration(cfg.Timeout) * time.Second
	Client = &fastapiClient{
		baseURL: cfg.BaseURL,
		httpClient: &http.Client{
			Transport: transport,
			Timeout:   timeout,
		},
		// SSE 流超时设为 3 分钟，给 LLM 足够的生成窗口
		streamClient: &http.Client{
			Transport: transport,
			Timeout:   180 * time.Second,
		},
	}
}

// PostForData 发起一次性 JSON POST，解包 data 字段后返回原始 JSON。
// 适用于 Agent/Workflow 运行等需要完整响应体的场景。
func (c *fastapiClient) PostForData(ctx context.Context, path string, body any) (json.RawMessage, error) {
	resp, err := c.doPost(ctx, c.httpClient, path, body)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	var result standardResp
	if err = json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, fmt.Errorf("解析 FastAPI 响应失败: %w", err)
	}
	if result.Code != 200 {
		return nil, fmt.Errorf("FastAPI 返回业务错误 [%d]: %s", result.Code, result.Message)
	}
	return result.Data, nil
}

// Send 发起带自定义超时的 JSON POST，仅关注是否成功，不读取响应体。
// 适用于嵌入构建/删除等耗时较长但无需回传数据的操作。
func (c *fastapiClient) Send(ctx context.Context, path string, body any, timeout time.Duration) error {
	client := &http.Client{
		Transport: c.httpClient.Transport,
		Timeout:   timeout,
	}
	resp, err := c.doPost(ctx, client, path, body)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	var result standardResp
	if err = json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return fmt.Errorf("解析 FastAPI 响应失败: %w", err)
	}
	if result.Code != 200 {
		return fmt.Errorf("FastAPI 返回业务错误 [%d]: %s", result.Code, result.Message)
	}
	return nil
}

// Stream 发起 SSE POST 请求，逐行调用 onLine 回调转发给上层调用者。
// 调用方通过 ctx 取消可中止流；onLine 中调用方应将行内容写入 gin.ResponseWriter 并 Flush。
// 空行（SSE 帧分隔符）会被跳过，保证回调仅接收 "data: ..." 格式的有效行。
func (c *fastapiClient) Stream(ctx context.Context, path string, body any, onLine func(line string) error) error {
	resp, err := c.doPost(ctx, c.streamClient, path, body)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	scanner := bufio.NewScanner(resp.Body)
	// 单行最大 512KB，防止超长 token 流撑爆默认缓冲区（默认 64KB）
	scanner.Buffer(make([]byte, 512*1024), 512*1024)

	for scanner.Scan() {
		line := scanner.Text()
		if strings.TrimSpace(line) == "" {
			// SSE 帧之间的空行是协议分隔符，跳过
			continue
		}
		if err = onLine(line); err != nil {
			return err
		}
		// 检查 ctx 是否已被取消（客户端断连），避免继续读取浪费资源
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}
	}
	return scanner.Err()
}

// doPost 构造并执行 HTTP POST 请求，统一设置 Content-Type 和 Authorization 头。
func (c *fastapiClient) doPost(ctx context.Context, client *http.Client, path string, body any) (*http.Response, error) {
	data, err := json.Marshal(body)
	if err != nil {
		return nil, fmt.Errorf("序列化请求体失败: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.baseURL+path, bytes.NewReader(data))
	if err != nil {
		return nil, fmt.Errorf("构造请求失败: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("请求 FastAPI %s 失败: %w", path, err)
	}
	if resp.StatusCode >= 500 {
		body, _ := io.ReadAll(resp.Body)
		resp.Body.Close()
		return nil, fmt.Errorf("FastAPI %s 返回 HTTP %d: %s", path, resp.StatusCode, string(body))
	}
	return resp, nil
}

// HealthCheck 探测 FastAPI 服务健康状态，用于 /api/monitor/health
func (c *fastapiClient) HealthCheck() (bool, int64, error) {
	start := time.Now()
	req, _ := http.NewRequest(http.MethodGet, c.baseURL+"/health", nil)
	resp, err := c.httpClient.Do(req)
	latency := time.Since(start).Milliseconds()
	if err != nil {
		return false, latency, err
	}
	defer resp.Body.Close()
	return resp.StatusCode >= 200 && resp.StatusCode < 400, latency, nil
}

// BaseURL 返回当前配置的 FastAPI 基础 URL，供 Monitor 服务使用
func (c *fastapiClient) BaseURL() string { return c.baseURL }
