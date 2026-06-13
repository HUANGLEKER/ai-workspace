package handler

import (
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

// ListAgents GET /api/agent/list
func ListAgents(c *gin.Context) {
	agents, err := service.AgentSvc.ListByUser(middleware.CurrentUserID(c))
	if err != nil {
		common.ServerError(c, err.Error())
		return
	}
	common.OK(c, agents)
}

// GetAgent GET /api/agent/:id
func GetAgent(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	a, err := service.AgentSvc.GetOwned(id, middleware.CurrentUserID(c))
	if err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, a)
}

// AddAgent POST /api/agent/add
func AddAgent(c *gin.Context) {
	var a model.Agent
	if err := c.ShouldBindJSON(&a); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.AgentSvc.Create(&a, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, a)
}

// UpdateAgent PUT /api/agent/update
func UpdateAgent(c *gin.Context) {
	var a model.Agent
	if err := c.ShouldBindJSON(&a); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.AgentSvc.Update(&a, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "更新成功")
}

// DeleteAgent DELETE /api/agent/delete/:id
func DeleteAgent(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	if err = service.AgentSvc.Delete(id, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "删除成功")
}

// RunAgent POST /api/agent/:id/run — 解析工具/MCP 规格后 POST 给 FastAPI 执行工具调用循环
func RunAgent(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	var req struct {
		Input     string `json:"input"`
		SessionID string `json:"sessionId"`
	}
	if err = c.ShouldBindJSON(&req); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	result, err := service.AgentSvc.Run(c.Request.Context(), id, middleware.CurrentUserID(c), req.Input, req.SessionID, middleware.IsAdmin(c))
	if err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, result)
}

// RunAgentStream POST /api/agent/:id/run/stream — SSE 流式运行，逐步下发 think→act 轨迹。
// 复用 chat/rag 的 SSE 透传模式：service 负责归属校验与工具解析，handler 代理 FastAPI 流。
func RunAgentStream(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	var req struct {
		Input     string `json:"input"`
		SessionID string `json:"sessionId"`
	}
	if err = c.ShouldBindJSON(&req); err != nil {
		common.BadRequest(c, err.Error())
		return
	}

	// 归属校验 + 工具解析在发送 SSE 头之前完成，越权/错误用普通 JSON 响应兜底
	body, err := service.AgentSvc.BuildRunBody(id, middleware.CurrentUserID(c), req.Input, req.SessionID, middleware.IsAdmin(c))
	if err != nil {
		handleBizError(c, err)
		return
	}

	prepareSSE(c)

	w := c.Writer
	flusher, canFlush := w.(http.Flusher)
	ctx := c.Request.Context()

	_ = fastapi.Client.Stream(ctx, "/agent/run/stream", body, func(line string) error {
		if strings.TrimSpace(line) == "" {
			return nil
		}
		// step / answer / error / [DONE] 帧统一透传，由前端 streamSSE.extract 分拣
		fmt.Fprintf(w, "%s\n\n", line)
		if canFlush {
			flusher.Flush()
		}
		return nil
	})
}
