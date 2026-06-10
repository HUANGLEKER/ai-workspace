package handler

import (
	"github.com/gin-gonic/gin"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/middleware"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/internal/service"
)

// ListPrompts GET /api/prompt/list?keyword=&category=
func ListPrompts(c *gin.Context) {
	keyword := c.Query("keyword")
	category := c.Query("category")
	prompts, err := service.PromptSvc.ListByUser(middleware.CurrentUserID(c), keyword, category)
	if err != nil {
		common.ServerError(c, err.Error())
		return
	}
	common.OK(c, prompts)
}

// GetPrompt GET /api/prompt/:id
func GetPrompt(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	p, err := service.PromptSvc.GetOwned(id, middleware.CurrentUserID(c))
	if err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, p)
}

// AddPrompt POST /api/prompt/add
func AddPrompt(c *gin.Context) {
	var p model.Prompt
	if err := c.ShouldBindJSON(&p); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.PromptSvc.Create(&p, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OK(c, p)
}

// UpdatePrompt PUT /api/prompt/update
func UpdatePrompt(c *gin.Context) {
	var p model.Prompt
	if err := c.ShouldBindJSON(&p); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.PromptSvc.Update(&p, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "更新成功")
}

// DeletePrompt DELETE /api/prompt/delete/:id
func DeletePrompt(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	if err = service.PromptSvc.Delete(id, middleware.CurrentUserID(c)); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "删除成功")
}
