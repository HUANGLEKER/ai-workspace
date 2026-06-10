package handler

import (
	"github.com/gin-gonic/gin"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/middleware"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/internal/service"
)

// ListMCPs GET /api/mcp/list
func ListMCPs(c *gin.Context) {
	servers, err := service.MCPSvc.ListByUser(middleware.CurrentUserID(c))
	if err != nil {
		common.ServerError(c, err.Error())
		return
	}
	common.OK(c, servers)
}

// GetMCP GET /api/mcp/:id
func GetMCP(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	srv, err := service.MCPSvc.GetOwned(id, middleware.CurrentUserID(c))
	if err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, srv)
}

// AddMCP POST /api/mcp/add
func AddMCP(c *gin.Context) {
	var srv model.McpServer
	if err := c.ShouldBindJSON(&srv); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.MCPSvc.Create(&srv, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, srv)
}

// UpdateMCP PUT /api/mcp/update
func UpdateMCP(c *gin.Context) {
	var srv model.McpServer
	if err := c.ShouldBindJSON(&srv); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.MCPSvc.Update(&srv, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "更新成功")
}

// DeleteMCP DELETE /api/mcp/delete/:id
func DeleteMCP(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	if err = service.MCPSvc.Delete(id, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "删除成功")
}

// TestMCP POST /api/mcp/test/:id — 对 SSE 类型 MCP 服务器发起连通性探测
func TestMCP(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	ok, latency, msg := service.MCPSvc.TestConnectivity(id, middleware.CurrentUserID(c))
	common.OK(c, gin.H{
		"reachable": ok,
		"latency":   latency,
		"message":   msg,
	})
}
