package service

import (
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
	"gorm.io/gorm"
)

// MCPSvc 是 MCP 服务器服务全局单例
var MCPSvc *MCPService

// MCPService 依赖经构造函数注入（P1-1）
type MCPService struct {
	db *gorm.DB
}

// NewMCPService 构造服务
func NewMCPService(db *gorm.DB) *MCPService {
	return &MCPService{db: db}
}

// ListByUser 查询当前用户注册的所有 MCP 服务器
func (s *MCPService) ListByUser(userID int64) ([]model.McpServer, error) {
	var servers []model.McpServer
	err := s.db.Scopes(ownedScope[model.McpServer](userID)).Order("create_time DESC").Find(&servers).Error
	return servers, err
}

// GetOwned 查询并校验 MCP 服务器归属，防止 IDOR
func (s *MCPService) GetOwned(id, userID int64) (*model.McpServer, error) {
	return getOwnedResource[model.McpServer](s.db, id, userID, "MCP服务器")
}

// Create 新建 MCP 服务器，强制 CreateBy 为当前用户
func (s *MCPService) Create(srv *model.McpServer, userID int64, isAdmin bool) error {
	if srv.Name == "" {
		return common.NewBizError(common.CodeBadRequest, "MCP服务器名称不能为空")
	}
	if err := guardStdio(srv, isAdmin); err != nil {
		return err
	}
	srv.ID = 0
	srv.CreateBy = userID
	if srv.Enabled == 0 {
		srv.Enabled = 1
	}
	return s.db.Create(srv).Error
}

// guardStdio 拦截非管理员注册 stdio 类型 MCP——stdio 等于在 AI 服务主机执行任意命令，
// 仅管理员可注册（P3-5 安全约束）。同时校验 stdio 必须配 command。
func guardStdio(srv *model.McpServer, isAdmin bool) error {
	if srv.Transport != "stdio" {
		return nil
	}
	if !isAdmin {
		return common.NewBizError(common.CodeForbidden, "stdio 类型 MCP 服务器仅管理员可注册（将在服务主机执行命令）")
	}
	if srv.Command == "" {
		return common.NewBizError(common.CodeBadRequest, "stdio 类型必须填写启动命令")
	}
	return nil
}

// Update 更新 MCP 服务器，回填 CreateBy 防止归属被篡改
func (s *MCPService) Update(srv *model.McpServer, userID int64, isAdmin bool) error {
	existing, err := s.GetOwned(srv.ID, userID)
	if err != nil {
		return err
	}
	if err := guardStdio(srv, isAdmin); err != nil {
		return err
	}
	srv.CreateBy = existing.CreateBy
	// Save 全字段覆盖，回填创建时间防止 create_time 被写成零值
	srv.CreatedAt = existing.CreatedAt
	return s.db.Save(srv).Error
}

// Delete 校验归属后删除 MCP 服务器
func (s *MCPService) Delete(id, userID int64) error {
	if _, err := s.GetOwned(id, userID); err != nil {
		return err
	}
	return s.db.Delete(&model.McpServer{}, id).Error
}

// TestConnectivity 对 SSE 类型 MCP 服务器发起 HTTP 可达性探测
// stdio 类型不支持远程探测，直接返回说明信息
func (s *MCPService) TestConnectivity(id, userID int64) (bool, int64, string) {
	srv, err := s.GetOwned(id, userID)
	if err != nil {
		return false, 0, err.Error()
	}
	if srv.Transport != "sse" {
		return false, 0, "stdio 传输类型不支持远程连通性探测"
	}
	if srv.URL == "" {
		return false, 0, "SSE URL 为空"
	}

	start := time.Now()
	client := &http.Client{Timeout: 5 * time.Second}
	resp, err := client.Get(srv.URL)
	latency := time.Since(start).Milliseconds()
	if err != nil {
		return false, latency, err.Error()
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 200 && resp.StatusCode < 400 {
		return true, latency, fmt.Sprintf("HTTP %d", resp.StatusCode)
	}
	return false, latency, fmt.Sprintf("HTTP %d", resp.StatusCode)
}

// ResolveForAgent 按服务器名列表（JSON 数组字符串）解析用户名下已启用的 MCP 服务器配置，
// 供 FastAPI /agent/run 使用。sse 始终可用；stdio 仅在 isAdmin 时纳入（防御：注册已限管理员，
// 此处再次 gate，避免用户被降权后仍触发已注册的 stdio 命令执行）。
func (s *MCPService) ResolveForAgent(userID int64, serverNamesJSON string, isAdmin bool) ([]map[string]any, error) {
	var names []string
	if serverNamesJSON != "" && serverNamesJSON != "[]" {
		if err := json.Unmarshal([]byte(serverNamesJSON), &names); err != nil {
			return nil, err
		}
	}
	if len(names) == 0 {
		return []map[string]any{}, nil
	}

	var servers []model.McpServer
	err := s.db.Scopes(ownedScope[model.McpServer](userID)).
		Where("name IN ? AND enabled = 1", names).Find(&servers).Error
	if err != nil {
		return nil, err
	}

	result := make([]map[string]any, 0, len(servers))
	for _, srv := range servers {
		// config 扩展字段：headers（sse）/ args、env（stdio）
		var cfg struct {
			Headers map[string]string `json:"headers"`
			Args    []string          `json:"args"`
			Env     map[string]string `json:"env"`
		}
		if srv.Config != "" {
			_ = json.Unmarshal([]byte(srv.Config), &cfg)
		}

		switch srv.Transport {
		case "sse":
			headers := cfg.Headers
			if headers == nil {
				headers = map[string]string{}
			}
			result = append(result, map[string]any{
				"name": srv.Name, "url": srv.URL, "transport": "sse", "headers": headers,
			})
		case "stdio":
			if !isAdmin || srv.Command == "" {
				continue // stdio 仅管理员可运行；缺命令跳过
			}
			args := cfg.Args
			if args == nil {
				args = []string{}
			}
			env := cfg.Env
			if env == nil {
				env = map[string]string{}
			}
			result = append(result, map[string]any{
				"name": srv.Name, "transport": "stdio", "command": srv.Command, "args": args, "env": env,
			})
		}
	}
	return result, nil
}
