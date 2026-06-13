package handler

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/middleware"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/internal/service"
	"github.com/aiworkspace/backend/pkg/fastapi"
)

// ─── 会话 ─────────────────────────────────────────────────────────────

// ListSessions GET /api/chat/session/list — 查询当前用户的会话列表
func ListSessions(c *gin.Context) {
	userID := middleware.CurrentUserID(c)
	sessions, err := service.ChatSvc.ListSessions(userID)
	if err != nil {
		common.ServerError(c, err.Error())
		return
	}
	common.OK(c, sessions)
}

// AddSession POST /api/chat/session/add — 新建会话
func AddSession(c *gin.Context) {
	var sess model.ChatSession
	if err := c.ShouldBindJSON(&sess); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.ChatSvc.CreateSession(&sess, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, sess)
}

// RenameSession PUT /api/chat/session/:id — 重命名会话
func RenameSession(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	var req struct {
		Title string `json:"title" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.ChatSvc.RenameSession(id, middleware.CurrentUserID(c), req.Title); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "重命名成功")
}

// UpdateSessionPrompt PUT /api/chat/session/:id/prompt — 设置/清除会话级系统提示词
// 来自提示词中心的"设为系统提示词"动作；空 systemPrompt 表示清除
func UpdateSessionPrompt(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	var req struct {
		SystemPrompt string `json:"systemPrompt"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.ChatSvc.SetSystemPrompt(id, middleware.CurrentUserID(c), req.SystemPrompt); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "已更新系统提示词")
}

// DeleteSession DELETE /api/chat/session/:id — 删除会话（级联删除消息）
func DeleteSession(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	if err = service.ChatSvc.DeleteSession(id, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "删除成功")
}

// ListMessages GET /api/chat/session/:id/messages — 查询会话消息历史
func ListMessages(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	// 校验会话归属，防止越权读取他人消息
	if _, err = service.ChatSvc.GetOwnedSession(id, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	messages, err := service.ChatSvc.ListMessages(id)
	if err != nil {
		common.ServerError(c, err.Error())
		return
	}
	common.OK(c, messages)
}

// ─── SSE 对话 ────────────────────────────────────────────────────────

// ChatSend POST /api/chat/send — SSE 流式发送消息并实时返回 LLM 回复
//
// 流程：
//  1. 校验会话归属（防 IDOR）
//  2. 持久化用户消息
//  3. 取有界上下文历史（最近 20 条，控制 token 成本）
//  4. 代理 FastAPI /chat SSE 流到浏览器
//  5. 最终在 goroutine 内持久化 assistant 回复（断连保存）
func ChatSend(c *gin.Context) {
	var req struct {
		SessionID int64  `json:"sessionId" binding:"required"`
		Content   string `json:"content"   binding:"required"`
		Model     string `json:"model"`
		WebSearch bool   `json:"webSearch"` // 可选；开启后 FastAPI 经 LLM tool-calling 联网搜索
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		common.BadRequest(c, err.Error())
		return
	}

	userID := middleware.CurrentUserID(c)
	// 会话归属校验，越权直接 400/403
	sess, err := service.ChatSvc.GetOwnedSession(req.SessionID, userID)
	if err != nil {
		handleBizError(c, err)
		return
	}
	// 请求未指定模型时回退到会话创建时绑定的模型
	if req.Model == "" {
		req.Model = sess.ModelName
	}

	// 持久化用户消息，确保即使后续流失败也有记录
	if err := service.ChatSvc.SaveMessage(req.SessionID, "user", req.Content); err != nil {
		common.ServerError(c, "保存消息失败: "+err.Error())
		return
	}

	// 默认标题的会话用首条消息自动生成标题，置于发送 SSE 头之前以便用普通 JSON 错误响应兜底
	oldTitle := sess.Title
	autoTitle := service.ChatSvc.AutoTitleFromFirstMessage(sess, req.Content)
	titleChanged := autoTitle != oldTitle

	// 构建有界上下文：摘要已覆盖的旧消息排除，只取 id > summary_upto_id 的最近消息
	history, err := service.ChatSvc.RecentContextMessages(req.SessionID, sess.SummaryUptoID)
	if err != nil {
		common.ServerError(c, err.Error())
		return
	}
	messages := make([]map[string]string, 0, len(history)+2)
	// 会话绑定了系统提示词（来自提示词中心）：拼为上下文首条 system 消息，
	// FastAPI 侧按 role 映射为 SystemMessage，无需任何改动
	if sess.SystemPrompt != "" {
		messages = append(messages, map[string]string{"role": "system", "content": sess.SystemPrompt})
	}
	// Memory（P3-2）：早先对话摘要作为 system 消息注入，让模型保有长会话上文
	if sess.Summary != "" {
		messages = append(messages, map[string]string{"role": "system", "content": "以下是本次对话早先内容的摘要，供你保持上下文连贯：\n" + sess.Summary})
	}
	for _, m := range history {
		messages = append(messages, map[string]string{"role": m.Role, "content": m.Content})
	}

	body := map[string]any{
		"session_id":        fmt.Sprintf("%d", req.SessionID),
		"messages":          messages,
		"model":             req.Model,
		"stream":            true,
		"enable_web_search": req.WebSearch,
	}
	// 多模型路由：模型在 chat_model 表配了 api_url/api_key 则随请求透传，
	// 让 FastAPI 按请求构建客户端；未配置则 FastAPI 回退 .env 默认提供方
	if cfg := service.LlmConfigBody(service.ChatSvc.GetModelConfigByName(req.Model)); cfg != nil {
		body["llm_config"] = cfg
	}

	// 设置 SSE 响应头并清除写超时（长流不被 server WriteTimeout 截断）
	prepareSSE(c)
	c.Header("Connection", "keep-alive")

	w := c.Writer
	flusher, canFlush := w.(http.Flusher)

	// 标题已自动生成：先推一帧 title 元数据，前端据此实时更新侧边栏会话名
	if titleChanged {
		if titleFrame, mErr := json.Marshal(map[string]string{"type": "title", "title": autoTitle}); mErr == nil {
			fmt.Fprintf(w, "data: %s\n\n", titleFrame)
			if canFlush {
				flusher.Flush()
			}
		}
	}

	var assistantReply strings.Builder
	var completionTokens int
	ctx := c.Request.Context()

	streamErr := fastapi.Client.Stream(ctx, "/chat", body, func(line string) error {
		if !strings.HasPrefix(line, "data: ") {
			return nil
		}
		data := strings.TrimPrefix(line, "data: ")
		// [DONE] 哨兵表示流结束，透传给浏览器
		if strings.TrimSpace(data) == "[DONE]" {
			fmt.Fprintf(w, "data: [DONE]\n\n")
			if canFlush {
				flusher.Flush()
			}
			return nil
		}
		// 解析 token 并追加到 assistantReply 用于持久化
		var payload map[string]any
		if err := json.Unmarshal([]byte(data), &payload); err == nil {
			if token, ok := payload["token"].(string); ok {
				assistantReply.WriteString(token)
			}
			// usage 帧：记录本次回复的 completion_tokens，随 assistant 消息落库
			if t, ok := payload["type"].(string); ok && t == "usage" {
				if v, ok := payload["completion_tokens"].(float64); ok {
					completionTokens = int(v)
				}
			}
		}
		// 原样透传 SSE 行到浏览器
		fmt.Fprintf(w, "%s\n\n", line)
		if canFlush {
			flusher.Flush()
		}
		return nil
	})

	if streamErr != nil {
		// 客户端断连属于正常情况，仅在非 context canceled 时记录错误
		if ctx.Err() == nil {
			// 用 json.Marshal 序列化，避免错误信息中的引号破坏 JSON 帧
			errFrame, _ := json.Marshal(map[string]string{"error": streamErr.Error()})
			fmt.Fprintf(w, "data: %s\n\n", errFrame)
			if canFlush {
				flusher.Flush()
			}
		}
	}

	// 断连保存：不论客户端是否中途断开，只要有已生成内容就持久化
	if assistantReply.Len() > 0 {
		_ = service.ChatSvc.SaveMessageWithTokens(req.SessionID, "assistant", assistantReply.String(), completionTokens)
		// Memory（P3-2）：异步滚动摘要，消息数超阈值时压缩旧消息控制 token 成本
		llmCfg := service.LlmConfigBody(service.ChatSvc.GetModelConfigByName(req.Model))
		go service.ChatSvc.MaybeSummarize(req.SessionID, req.Model, llmCfg)
	}
}

// ─── 模型管理 ────────────────────────────────────────────────────────

// chatModelReq 模型增改请求体。
// model.ChatModel 的 ApiKey json tag 为 "-"（响应中脱敏），无法直接绑定请求，
// 故用独立 DTO 接收 apiKey 后映射到模型。
type chatModelReq struct {
	ID        int64  `json:"id"`
	ModelName string `json:"modelName"`
	Provider  string `json:"provider"`
	ApiUrl    string `json:"apiUrl"`
	ApiKey    string `json:"apiKey"`
	Enabled   int8   `json:"enabled"`
}

func (r *chatModelReq) toModel() *model.ChatModel {
	return &model.ChatModel{
		BaseModel: model.BaseModel{ID: r.ID},
		ModelName: r.ModelName,
		Provider:  r.Provider,
		ApiUrl:    r.ApiUrl,
		ApiKey:    r.ApiKey,
		Enabled:   r.Enabled,
	}
}

// ListChatModels GET /api/chat/model/list
func ListChatModels(c *gin.Context) {
	models, err := service.ChatSvc.ListModels()
	if err != nil {
		common.ServerError(c, err.Error())
		return
	}
	common.OK(c, models)
}

// PageChatModels GET /api/chat/model/page — 仅管理员，分页查询全部模型（含禁用）
func PageChatModels(c *gin.Context) {
	pg := common.ParsePage(c)
	result, err := service.ChatSvc.PageModels(pg.PageNum, pg.PageSize, c.Query("modelName"))
	if err != nil {
		common.ServerError(c, err.Error())
		return
	}
	common.OK(c, result)
}

// AddChatModel POST /api/chat/model/add — 仅管理员
func AddChatModel(c *gin.Context) {
	var req chatModelReq
	if err := c.ShouldBindJSON(&req); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	m := req.toModel()
	if err := service.ChatSvc.AddModel(m); err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, m)
}

// UpdateChatModel PUT /api/chat/model/update — 仅管理员；apiKey 留空表示保持原值
func UpdateChatModel(c *gin.Context) {
	var req chatModelReq
	if err := c.ShouldBindJSON(&req); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.ChatSvc.UpdateModel(req.toModel()); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "更新成功")
}

// UpdateChatModelStatus PUT /api/chat/model/status — 仅管理员，启用/禁用模型
func UpdateChatModelStatus(c *gin.Context) {
	var req struct {
		ID      int64 `json:"id" binding:"required"`
		Enabled int8  `json:"enabled"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.ChatSvc.UpdateModelStatus(req.ID, req.Enabled); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "状态更新成功")
}

// DeleteChatModel DELETE /api/chat/model/:id — 仅管理员
func DeleteChatModel(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	if err = service.ChatSvc.DeleteModel(id); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "删除成功")
}
