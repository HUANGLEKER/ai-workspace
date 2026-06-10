package handler

import (
	"errors"

	"golang.org/x/crypto/bcrypt"
	"gorm.io/gorm"

	"github.com/gin-gonic/gin"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/middleware"
	"github.com/aiworkspace/backend/internal/model"
	"github.com/aiworkspace/backend/internal/service"
	"github.com/aiworkspace/backend/pkg/database"
)

type loginReq struct {
	Username string `json:"username" binding:"required"`
	Password string `json:"password" binding:"required"`
}

type loginResp struct {
	Token    string   `json:"token"`
	UserID   int64    `json:"userId"`
	Username string   `json:"username"`
	Nickname string   `json:"nickname"`
	Roles    []string `json:"roles"`
}

// Login POST /api/auth/login — 验证用户名密码，颁发 JWT
func Login(c *gin.Context) {
	var req loginReq
	if err := c.ShouldBindJSON(&req); err != nil {
		common.BadRequest(c, "参数错误: "+err.Error())
		return
	}

	var user model.SysUser
	if err := database.DB.Where("username = ?", req.Username).First(&user).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			common.Fail(c, common.CodeUnauth, "用户名或密码错误")
		} else {
			common.ServerError(c, err.Error())
		}
		return
	}

	if user.Status == 0 {
		common.Fail(c, common.CodeForbidden, "账号已被禁用")
		return
	}

	// BCrypt 比较，防止时序攻击（bcrypt 内部已做常数时间比较）
	if err := bcrypt.CompareHashAndPassword([]byte(user.Password), []byte(req.Password)); err != nil {
		common.Fail(c, common.CodeUnauth, "用户名或密码错误")
		return
	}

	roles := service.UserSvc.GetRoles(user.ID)
	token, err := middleware.GenerateToken(user.ID, user.Username, roles)
	if err != nil {
		common.ServerError(c, "生成 Token 失败")
		return
	}

	common.OK(c, loginResp{
		Token:    token,
		UserID:   user.ID,
		Username: user.Username,
		Nickname: user.Nickname,
		Roles:    roles,
	})
}

// Logout POST /api/auth/logout — 无状态 JWT，客户端丢弃 Token 即可
func Logout(c *gin.Context) {
	common.OKMsg(c, "退出成功")
}

type authInfoResp struct {
	UserID   int64    `json:"userId"`
	Username string   `json:"username"`
	Nickname string   `json:"nickname"`
	Avatar   string   `json:"avatar"`
	Roles    []string `json:"roles"`
	IsAdmin  bool     `json:"isAdmin"`
}

// GetAuthInfo GET /api/auth/info — 返回当前登录用户信息与角色列表
func GetAuthInfo(c *gin.Context) {
	userID := middleware.CurrentUserID(c)
	user, err := service.UserSvc.GetByID(userID)
	if err != nil {
		common.ServerError(c, "获取用户信息失败")
		return
	}

	roles := service.UserSvc.GetRoles(userID)
	isAdmin := false
	for _, r := range roles {
		if r == "ROLE_ADMIN" {
			isAdmin = true
			break
		}
	}

	common.OK(c, authInfoResp{
		UserID:   user.ID,
		Username: user.Username,
		Nickname: user.Nickname,
		Avatar:   user.Avatar,
		Roles:    roles,
		IsAdmin:  isAdmin,
	})
}
