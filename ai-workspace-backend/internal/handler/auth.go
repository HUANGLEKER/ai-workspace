package handler

import (
	"errors"
	"strings"

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

// Logout POST /api/auth/logout — 将当前 token 加入 Redis 黑名单（jti，TTL=剩余有效期）。
// 路由保持公开：即使带的是已失效 token 也应能"成功登出"，前端无需处理失败分支。
func Logout(c *gin.Context) {
	auth := c.GetHeader("Authorization")
	if strings.HasPrefix(auth, "Bearer ") {
		if claims, err := middleware.ParseToken(strings.TrimPrefix(auth, "Bearer ")); err == nil {
			middleware.RevokeToken(claims)
		}
	}
	common.OKMsg(c, "退出成功")
}

// RefreshToken POST /api/auth/refresh — 用仍有效的 token 换取新 token（轮换：旧 token 立即吊销）。
// 挂在 JWTAuth 之后，已过期/已吊销的 token 在中间件层即被拒绝。
func RefreshToken(c *gin.Context) {
	v, _ := c.Get(middleware.CtxClaims)
	claims, ok := v.(*middleware.Claims)
	if !ok {
		common.Unauthorized(c)
		return
	}
	// 角色实时重查：刷新窗口内被改权限/禁用的用户不应延续旧角色
	user, err := service.UserSvc.GetByID(claims.UserID)
	if err != nil || user.Status == 0 {
		common.Unauthorized(c)
		return
	}
	roles := service.UserSvc.GetRoles(claims.UserID)
	token, err := middleware.GenerateToken(claims.UserID, claims.Username, roles)
	if err != nil {
		common.ServerError(c, "生成 Token 失败")
		return
	}
	middleware.RevokeToken(claims)
	common.OK(c, gin.H{"token": token})
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

// UpdatePassword PUT /api/auth/password — 用户修改自己的密码
func UpdatePassword(c *gin.Context) {
	var req struct {
		OldPassword string `json:"oldPassword" binding:"required"`
		NewPassword string `json:"newPassword" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		common.BadRequest(c, err.Error())
		return
	}

	userID := middleware.CurrentUserID(c)
	if err := service.UserSvc.UpdatePassword(userID, req.OldPassword, req.NewPassword); err != nil {
		handleBizError(c, err)
		return
	}

	common.OKMsg(c, "密码修改成功，请重新登录")
}
