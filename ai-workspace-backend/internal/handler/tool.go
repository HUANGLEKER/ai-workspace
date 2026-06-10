package handler

import (
	"github.com/gin-gonic/gin"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/middleware"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/internal/service"
)

// ListTools GET /api/tool/list
func ListTools(c *gin.Context) {
	tools, err := service.ToolSvc.ListByUser(middleware.CurrentUserID(c))
	if err != nil {
		common.ServerError(c, err.Error())
		return
	}
	common.OK(c, tools)
}

// GetTool GET /api/tool/:id
func GetTool(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	t, err := service.ToolSvc.GetOwned(id, middleware.CurrentUserID(c))
	if err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, t)
}

// AddTool POST /api/tool/add
func AddTool(c *gin.Context) {
	var t model.Tool
	if err := c.ShouldBindJSON(&t); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.ToolSvc.Create(&t, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, t)
}

// UpdateTool PUT /api/tool/update
func UpdateTool(c *gin.Context) {
	var t model.Tool
	if err := c.ShouldBindJSON(&t); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.ToolSvc.Update(&t, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "更新成功")
}

// DeleteTool DELETE /api/tool/delete/:id
func DeleteTool(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	if err = service.ToolSvc.Delete(id, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "删除成功")
}
