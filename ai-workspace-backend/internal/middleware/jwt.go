package middleware

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
	"go.uber.org/zap"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/config"
	redisPkg "github.com/aiworkspace/backend/pkg/redis"
)

const CtxUserID = "userID"
const CtxRoles = "roles"

// CtxClaims 注入完整 Claims，供登出吊销/刷新轮换读取 jti 与过期时间
const CtxClaims = "claims"

type Claims struct {
	UserID   int64    `json:"userId"`
	Username string   `json:"username"`
	Roles    []string `json:"roles"`
	jwt.RegisteredClaims
}

// GenerateToken 登录成功后颁发 JWT，对应 JwtUtil.generateToken
func GenerateToken(userID int64, username string, roles []string) (string, error) {
	cfg := config.Global.JWT
	claims := Claims{
		UserID:   userID,
		Username: username,
		Roles:    roles,
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(time.Duration(cfg.Expire) * time.Second)),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
			// jti：每个 token 唯一标识，登出/刷新时进 Redis 黑名单实现吊销
			ID: newJTI(),
		},
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return token.SignedString([]byte(cfg.Secret))
}

// ParseToken 解析并校验 JWT
func ParseToken(tokenStr string) (*Claims, error) {
	token, err := jwt.ParseWithClaims(tokenStr, &Claims{}, func(t *jwt.Token) (any, error) {
		return []byte(config.Global.JWT.Secret), nil
	})
	if err != nil {
		return nil, err
	}
	claims, ok := token.Claims.(*Claims)
	if !ok || !token.Valid {
		// err 为 nil 但 token 无效时必须返回非 nil 错误，否则调用方会解引用 nil claims
		return nil, errors.New("invalid token")
	}
	return claims, nil
}

// newJTI 生成 128 位随机 token 标识
func newJTI() string {
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		// crypto/rand 失败极罕见；退化为时间戳，仍可用于黑名单（弱唯一性）
		return hex.EncodeToString([]byte(time.Now().String()))[:32]
	}
	return hex.EncodeToString(b)
}

func revocationKey(jti string) string { return "jwt:revoked:" + jti }

// RevokeToken 将 token 加入 Redis 黑名单，TTL 为剩余有效期（过期自动清理）。
// 登出与刷新轮换共用此入口。
func RevokeToken(claims *Claims) {
	if claims.ID == "" || claims.ExpiresAt == nil {
		return // 旧版无 jti 的 token 无法吊销，只能等自然过期
	}
	ttl := time.Until(claims.ExpiresAt.Time)
	if ttl <= 0 {
		return
	}
	if err := redisPkg.Client.Set(context.Background(), revocationKey(claims.ID), 1, ttl).Err(); err != nil {
		zap.L().Warn("写入 token 黑名单失败", zap.Error(err))
	}
}

// isRevoked 检查 token 是否已被吊销。Redis 不可用时 fail-open 放行：
// 黑名单是登出后的补充防线，不能让 Redis 故障打挂全部鉴权。
func isRevoked(jti string) bool {
	if jti == "" {
		return false
	}
	n, err := redisPkg.Client.Exists(context.Background(), revocationKey(jti)).Result()
	if err != nil {
		zap.L().Warn("token 黑名单检查失败，fail-open", zap.Error(err))
		return false
	}
	return n > 0
}

// JWTAuth 认证中间件，对应 JwtAuthFilter
func JWTAuth() gin.HandlerFunc {
	return func(c *gin.Context) {
		auth := c.GetHeader("Authorization")
		if !strings.HasPrefix(auth, "Bearer ") {
			common.Unauthorized(c)
			c.Abort()
			return
		}
		claims, err := ParseToken(strings.TrimPrefix(auth, "Bearer "))
		if err != nil {
			common.Unauthorized(c)
			c.Abort()
			return
		}
		// 已登出/已轮换的 token 即使签名有效也拒绝
		if isRevoked(claims.ID) {
			common.Unauthorized(c)
			c.Abort()
			return
		}
		c.Set(CtxUserID, claims.UserID)
		c.Set(CtxRoles, claims.Roles)
		c.Set(CtxClaims, claims)
		c.Next()
	}
}

// AdminRequired 对应 @PreAuthorize("hasRole('ADMIN')")
func AdminRequired() gin.HandlerFunc {
	return func(c *gin.Context) {
		// 安全断言：路由误配（未先挂 JWTAuth）时返回 403 而不是 panic
		for _, r := range c.GetStringSlice(CtxRoles) {
			if r == "ROLE_ADMIN" {
				c.Next()
				return
			}
		}
		common.Forbidden(c)
		c.Abort()
	}
}

// CurrentUserID 从 context 中取当前用户 ID
func CurrentUserID(c *gin.Context) int64 {
	id, _ := c.Get(CtxUserID)
	return id.(int64)
}

// IsAdmin 判断当前请求用户是否具备 ROLE_ADMIN，供需要管理员才能执行的非 admin-only 路由内联判断
func IsAdmin(c *gin.Context) bool {
	for _, r := range c.GetStringSlice(CtxRoles) {
		if r == "ROLE_ADMIN" {
			return true
		}
	}
	return false
}
