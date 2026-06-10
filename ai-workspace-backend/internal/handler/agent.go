package handler

import (
	"github.com/gin-gonic/gin"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/middleware"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/internal/service"
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
	result, err := service.AgentSvc.Run(c.Request.Context(), id, middleware.CurrentUserID(c), req.Input, req.SessionID)
	if err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, result)
}
