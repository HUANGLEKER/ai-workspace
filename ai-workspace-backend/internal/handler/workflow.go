package handler

import (
	"github.com/gin-gonic/gin"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/middleware"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/internal/service"
)

// ListWorkflows GET /api/workflow/list
func ListWorkflows(c *gin.Context) {
	wfs, err := service.WorkflowSvc.ListByUser(middleware.CurrentUserID(c))
	if err != nil {
		common.ServerError(c, err.Error())
		return
	}
	common.OK(c, wfs)
}

// GetWorkflow GET /api/workflow/:id
func GetWorkflow(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	wf, err := service.WorkflowSvc.GetOwned(id, middleware.CurrentUserID(c))
	if err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, wf)
}

// AddWorkflow POST /api/workflow/add
func AddWorkflow(c *gin.Context) {
	var wf model.Workflow
	if err := c.ShouldBindJSON(&wf); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.WorkflowSvc.Create(&wf, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, wf)
}

// UpdateWorkflow PUT /api/workflow/update
func UpdateWorkflow(c *gin.Context) {
	var wf model.Workflow
	if err := c.ShouldBindJSON(&wf); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.WorkflowSvc.Update(&wf, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "更新成功")
}

// DeleteWorkflow DELETE /api/workflow/delete/:id
func DeleteWorkflow(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	if err = service.WorkflowSvc.Delete(id, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "删除成功")
}

// RunWorkflow POST /api/workflow/:id/run — 将工作流定义 POST 给 FastAPI LangGraph 引擎执行
func RunWorkflow(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	var req struct {
		Input string `json:"input"`
	}
	if err = c.ShouldBindJSON(&req); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	result, err := service.WorkflowSvc.Run(c.Request.Context(), id, middleware.CurrentUserID(c), req.Input)
	if err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, result)
}
