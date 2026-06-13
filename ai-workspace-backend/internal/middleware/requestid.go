package middleware

import (
	"github.com/gin-gonic/gin"

	"github.com/aiworkspace/backend/pkg/reqid"
)

// RequestID 为每条请求生成（或沿用上游传入的）X-Request-ID：
// 写入响应头供前端排障引用，写入 request context 供日志与 FastAPI 客户端透传。
// 必须挂在 RequestLogger 之前。
func RequestID() gin.HandlerFunc {
	return func(c *gin.Context) {
		rid := c.GetHeader(reqid.Header)
		if rid == "" {
			rid = newJTI()[:16]
		}
		c.Header(reqid.Header, rid)
		c.Request = c.Request.WithContext(reqid.With(c.Request.Context(), rid))
		c.Next()
	}
}
