package handler

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/middleware"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/internal/service"
	"github.com/aiworkspace/backend/pkg/fastapi"
)

// ─── 知识库问答会话 ───────────────────────────────────────────────────
//
// 与 chat 会话同构但独立成表（rag_session/rag_message），额外绑定一个知识库。
// 让知识库问答具备「会话历史持久化 + 多轮上下文 + 引用来源落库」能力。

// ListRagSessions GET /api/rag/session/list?kbId= — 查询当前用户的问答会话（可按知识库过滤）
func ListRagSessions(c *gin.Context) {
	kbID, _ := strconv.ParseInt(c.Query("kbId"), 10, 64)
	sessions, err := service.RagSvc.ListSessions(middleware.CurrentUserID(c), kbID)
	if err != nil {
		common.ServerError(c, err.Error())
		return
	}
	common.OK(c, sessions)
}

// AddRagSession POST /api/rag/session/add — 新建问答会话（绑定知识库）
func AddRagSession(c *gin.Context) {
	var sess model.RagSession
	if err := c.ShouldBindJSON(&sess); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if sess.KbID <= 0 {
		common.BadRequest(c, "kbId 无效")
		return
	}
	userID := middleware.CurrentUserID(c)
	// 校验知识库归属，防止把会话绑到他人知识库
	if _, err := service.KBSvc.GetOwned(sess.KbID, userID); err != nil {
		handleBizError(c, err)
		return
	}
	if err := service.RagSvc.CreateSession(&sess, userID); err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, sess)
}

// RenameRagSession PUT /api/rag/session/:id — 重命名问答会话
func RenameRagSession(c *gin.Context) {
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
	if err := service.RagSvc.RenameSession(id, middleware.CurrentUserID(c), req.Title); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "重命名成功")
}

// UpdateRagSessionPrompt PUT /api/rag/session/:id/prompt — 设置/清除会话级系统提示词
// 来自提示词中心的「设为系统提示词」动作；空 systemPrompt 表示清除
func UpdateRagSessionPrompt(c *gin.Context) {
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
	if err := service.RagSvc.SetSystemPrompt(id, middleware.CurrentUserID(c), req.SystemPrompt); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "已更新系统提示词")
}

// DeleteRagSession DELETE /api/rag/session/:id — 删除问答会话（级联删除消息）
func DeleteRagSession(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	if err = service.RagSvc.DeleteSession(id, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "删除成功")
}

// ClearRagMessages DELETE /api/rag/session/:id/messages — 清空会话消息（保留会话）
func ClearRagMessages(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	if err = service.RagSvc.ClearMessages(id, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "已清空")
}

// ListRagMessages GET /api/rag/session/:id/messages — 查询会话消息历史
func ListRagMessages(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	// 校验会话归属，防止越权读取他人消息
	if _, err = service.RagSvc.GetOwnedSession(id, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	messages, err := service.RagSvc.ListMessages(id)
	if err != nil {
		common.ServerError(c, err.Error())
		return
	}
	common.OK(c, messages)
}

// ─── SSE 知识库问答 ──────────────────────────────────────────────────

// RAGChat POST /api/rag/chat — SSE 流式 RAG 问答，代理 FastAPI /rag/chat
//
// 流程（对齐 chat 的持久化 + 断连保存）：
//  1. 校验会话与知识库归属（防 IDOR）
//  2. 组装多轮上下文历史（最近 N 条），再持久化本轮用户问题
//  3. 默认标题会话用首条问题自动起名，先推 title 元数据帧
//  4. 代理 FastAPI /rag/chat SSE 流到浏览器，沿途缓冲答案 + 来源 + token
//  5. 流结束/断连后持久化 assistant 回复（含 sources JSON）
func RAGChat(c *gin.Context) {
	var req struct {
		SessionID int64  `json:"sessionId" binding:"required"`
		Question  string `json:"question"  binding:"required"`
		TopK      int    `json:"topK"`
		Model     string `json:"model"`     // 可选；指定则按 chat_model 配置做多模型路由
		WebSearch bool   `json:"webSearch"` // 可选；开启后 FastAPI 检索图并入联网搜索召回
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		common.BadRequest(c, err.Error())
		return
	}

	userID := middleware.CurrentUserID(c)
	// 校验问答会话归属
	sess, err := service.RagSvc.GetOwnedSession(req.SessionID, userID)
	if err != nil {
		handleBizError(c, err)
		return
	}
	// 校验绑定的知识库仍归属当前用户，防止越权检索
	if _, err := service.KBSvc.GetOwned(sess.KbID, userID); err != nil {
		handleBizError(c, err)
		return
	}

	if req.TopK <= 0 {
		req.TopK = 4
	}
	if req.Model == "" {
		req.Model = sess.ModelName
	}

	// 多轮上下文：取本轮之前的历史消息，再持久化当前问题（避免把当前问题混入 history）
	history, err := service.RagSvc.RecentContextMessages(req.SessionID)
	if err != nil {
		common.ServerError(c, err.Error())
		return
	}
	historyMsgs := make([]map[string]string, 0, len(history))
	for _, m := range history {
		historyMsgs = append(historyMsgs, map[string]string{"role": m.Role, "content": m.Content})
	}

	// 持久化用户问题，确保即使后续流失败也有记录
	if err := service.RagSvc.SaveMessage(req.SessionID, "user", req.Question); err != nil {
		common.ServerError(c, "保存消息失败: "+err.Error())
		return
	}

	// 默认标题会话用首条问题自动起名，置于发送 SSE 头之前以便普通 JSON 错误兜底
	oldTitle := sess.Title
	autoTitle := service.RagSvc.AutoTitleFromFirstMessage(sess, req.Question)
	titleChanged := autoTitle != oldTitle

	// FastAPI 的 Pydantic 模型要求 kb_id 为字符串，传整数会触发 422 校验错误
	body := map[string]any{
		"kb_id":             strconv.FormatInt(sess.KbID, 10),
		"question":          req.Question,
		"session_id":        strconv.FormatInt(req.SessionID, 10),
		"top_k":             req.TopK,
		"stream":            true,
		"enable_web_search": req.WebSearch,
		"history":           historyMsgs,
	}
	// 会话绑定了系统提示词（来自提示词中心）：透传给 FastAPI，与 RAG 引用规则
	// 一并注入为 system 消息，让回答符合提示词设定的风格/角色
	if sess.SystemPrompt != "" {
		body["system_prompt"] = sess.SystemPrompt
	}
	if req.Model != "" {
		body["model"] = req.Model
		if cfg := service.LlmConfigBody(service.ChatSvc.GetModelConfigByName(req.Model)); cfg != nil {
			body["llm_config"] = cfg
		}
	}

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

	var answer strings.Builder
	var sourcesJSON string
	var completionTokens int
	ctx := c.Request.Context()

	_ = fastapi.Client.Stream(ctx, "/rag/chat", body, func(line string) error {
		if strings.TrimSpace(line) == "" {
			return nil
		}
		if strings.HasPrefix(line, "data: ") {
			data := strings.TrimPrefix(line, "data: ")
			if strings.TrimSpace(data) != "[DONE]" {
				var payload map[string]any
				if err := json.Unmarshal([]byte(data), &payload); err == nil {
					if token, ok := payload["token"].(string); ok {
						answer.WriteString(token)
					}
					switch payload["type"] {
					case "sources":
						// 缓存最新一版 sources（结束前会重发带 cited 的修订版），随 assistant 落库
						if raw, mErr := json.Marshal(payload["sources"]); mErr == nil {
							sourcesJSON = string(raw)
						}
					case "usage":
						if v, ok := payload["completion_tokens"].(float64); ok {
							completionTokens = int(v)
						}
					}
				}
			}
		}
		// 原样透传 SSE 行到浏览器，由前端 streamSSE.extract / onMeta 分拣
		fmt.Fprintf(w, "%s\n\n", line)
		if canFlush {
			flusher.Flush()
		}
		return nil
	})

	// 断连保存：只要有已生成内容就持久化（含来源 JSON）
	if answer.Len() > 0 {
		_ = service.RagSvc.SaveAssistantMessage(req.SessionID, answer.String(), sourcesJSON, completionTokens)
	}
}
