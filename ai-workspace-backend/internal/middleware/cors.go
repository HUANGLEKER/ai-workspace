package middleware

import (
	"net/http"

	"github.com/gin-gonic/gin"

	"github.com/aiworkspace/backend/internal/config"
)

// CORS 跨域中间件（P3-6 收紧）：allowed_origins 含 "*" 或为空时放通所有来源（本地/单用户）；
// 否则仅回显白名单内的来源。本服务用 Bearer token 鉴权（非 Cookie），不开启凭证。
func CORS() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Header("Access-Control-Allow-Origin", resolveOrigin(c.GetHeader("Origin")))
		c.Header("Vary", "Origin")
		c.Header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		c.Header("Access-Control-Allow-Headers", "Content-Type, Authorization")
		c.Header("Access-Control-Max-Age", "86400")

		if c.Request.Method == http.MethodOptions {
			c.AbortWithStatus(http.StatusNoContent)
			return
		}
		c.Next()
	}
}

// resolveOrigin 按白名单决定回显的 Allow-Origin：
// 白名单为空或含 "*" → "*"；否则命中白名单回显该来源，未命中回显第一个白名单项（拒绝跨域）。
func resolveOrigin(reqOrigin string) string {
	allowed := config.Global.CORS.AllowedOrigins
	if len(allowed) == 0 {
		return "*"
	}
	for _, o := range allowed {
		if o == "*" {
			return "*"
		}
		if o == reqOrigin {
			return reqOrigin
		}
	}
	return allowed[0]
}
