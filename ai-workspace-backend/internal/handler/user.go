package handler

import (
	"strconv"

	"github.com/gin-gonic/gin"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/internal/service"
)

// ListUsers GET /api/user/page — 仅管理员，分页查询用户列表
func ListUsers(c *gin.Context) {
	pg := common.ParsePage(c)
	username := c.Query("username")
	result, err := service.UserSvc.PageUsers(pg.PageNum, pg.PageSize, username)
	if err != nil {
		common.ServerError(c, err.Error())
		return
	}
	common.OK(c, result)
}

// AddUser POST /api/user/add — 仅管理员，新增用户
func AddUser(c *gin.Context) {
	var user model.SysUser
	if err := c.ShouldBindJSON(&user); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.UserSvc.AddUser(&user); err != nil {
		handleBizError(c, err)
		return
	}
	user.Password = "" // 清除密码字段后再返回
	common.OK(c, user)
}

// UpdateUser PUT /api/user/update — 仅管理员，更新用户信息
func UpdateUser(c *gin.Context) {
	var user model.SysUser
	if err := c.ShouldBindJSON(&user); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.UserSvc.UpdateUser(&user); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "更新成功")
}

// DeleteUser DELETE /api/user/delete/:id — 仅管理员，删除用户
func DeleteUser(c *gin.Context) {
	id, err := parseID(c)
	if err != nil {
		return
	}
	if err = service.UserSvc.DeleteUser(id); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "删除成功")
}

// UpdateUserStatus PUT /api/user/status — 仅管理员，启用/禁用用户
func UpdateUserStatus(c *gin.Context) {
	var req struct {
		ID     int64 `json:"id"     binding:"required"`
		Status int8  `json:"status"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		common.BadRequest(c, err.Error())
		return
	}
	if err := service.UserSvc.UpdateStatus(req.ID, req.Status); err != nil {
		handleBizError(c, err)
		return
	}
	common.OKMsg(c, "状态更新成功")
}

// ─── 工具函数 ────────────────────────────────────────────────────────

// parseID 从路径参数 :id 解析 int64，失败时直接写响应并返回错误
func parseID(c *gin.Context) (int64, error) {
	id, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		common.BadRequest(c, "无效的 ID 格式")
		return 0, err
	}
	return id, nil
}

// handleBizError 将 BusinessError 翻译为对应的 HTTP 响应，其他错误返回 500
func handleBizError(c *gin.Context, err error) {
	if bizErr, ok := err.(*common.BusinessError); ok {
		common.Fail(c, bizErr.Code, bizErr.Message)
		return
	}
	common.ServerError(c, err.Error())
}
