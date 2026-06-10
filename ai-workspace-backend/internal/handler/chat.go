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
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		common.BadRequest(c, err.Error())
		return
	}

	userID := middleware.CurrentUserID(c)
	// 会话归属校验，越权直接 400/403
	if _, err := service.ChatSvc.GetOwnedSession(req.SessionID, userID); err != nil {
		handleBizError(c, err)
		return
	}

	// 持久化用户消息，确保即使后续流失败也有记录
	if err := service.ChatSvc.SaveMessage(req.SessionID, "user", req.Content); err != nil {
		common.ServerError(c, "保存消息失败: "+err.Error())
		return
	}

	// 构建有界上下文
	history, err := service.ChatSvc.ListRecentMessages(req.SessionID)
	if err != nil {
		common.ServerError(c, err.Error())
		return
	}
	messages := make([]map[string]string, 0, len(history))
	for _, m := range history {
		messages = append(messages, map[string]string{"role": m.Role, "content": m.Content})
	}

	body := map[string]any{
		"session_id": fmt.Sprintf("%d", req.SessionID),
		"messages":   messages,
		"model":      req.Model,
		"stream":     true,
	}

	// 设置 SSE 响应头
	c.Header("Content-Type", "text/event-stream")
	c.Header("Cache-Control", "no-cache")
	c.Header("X-Accel-Buffering", "no")
	c.Header("Connection", "keep-alive")

	w := c.Writer
	flusher, canFlush := w.(http.Flusher)

	var assistantReply strings.Builder
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
			fmt.Fprintf(w, "data: {\"error\":\"%s\"}\n\n", streamErr.Error())
			if canFlush {
				flusher.Flush()
			}
		}
	}

	// 断连保存：不论客户端是否中途断开，只要有已生成内容就持久化
	if assistantReply.Len() > 0 {
		_ = service.ChatSvc.SaveMessage(req.SessionID, "assistant", assistantReply.String())
	}
}

// ─── 模型管理 ────────────────────────────────────────────────────────

// ListChatModels GET /api/chat/model/list
func ListChatModels(c *gin.Context) {
	models, err := service.ChatSvc.ListModels()
	if err != nil {
		common.ServerError(c, err.Error())
		return
	}
	common.OK(c, models)
}

// AddChatModel POST /api/chat/model/add — 仅管理员
func AddChatModel(c *gin.Context) {
	var m model.ChatModel
	if err := c.ShouldBindJSON(&m); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.ChatSvc.AddModel(&m); err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, m)
}

// UpdateChatModel PUT /api/chat/model/update — 仅管理员
func UpdateChatModel(c *gin.Context) {
	var m model.ChatModel
	if err := c.ShouldBindJSON(&m); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.ChatSvc.UpdateModel(&m); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "更新成功")
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
