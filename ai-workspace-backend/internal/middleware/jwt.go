package middleware

import (
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/config"
)

const CtxUserID = "userID"
const CtxRoles = "roles"

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
	if err != nil || !token.Valid {
		return nil, err
	}
	return token.Claims.(*Claims), nil
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
		c.Set(CtxUserID, claims.UserID)
		c.Set(CtxRoles, claims.Roles)
		c.Next()
	}
}

// AdminRequired 对应 @PreAuthorize("hasRole('ADMIN')")
func AdminRequired() gin.HandlerFunc {
	return func(c *gin.Context) {
		roles, _ := c.Get(CtxRoles)
		for _, r := range roles.([]string) {
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
