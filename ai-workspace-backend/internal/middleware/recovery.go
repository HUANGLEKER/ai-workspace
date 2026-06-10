package middleware

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"go.uber.org/zap"

	"github.com/aiworkspace/backend/internal/common"
)

// Recovery 捕获 panic 并统一返回 500，同时记录堆栈，对应 GlobalExceptionHandler
func Recovery() gin.HandlerFunc {
	return func(c *gin.Context) {
		defer func() {
			if err := recover(); err != nil {
				zap.L().Error("panic recovered",
					zap.Any("error", err),
					zap.String("path", c.Request.URL.Path),
				)
				c.AbortWithStatusJSON(http.StatusOK, common.Result[any]{
					Code:    common.CodeServerError,
					Message: "服务器内部错误",
				})
			}
		}()

		c.Next()

		// 统一处理 BusinessError，无需每个 handler 手动判断
		for _, err := range c.Errors {
			if bizErr, ok := err.Err.(*common.BusinessError); ok {
				c.JSON(http.StatusOK, common.Result[any]{
					Code:    bizErr.Code,
					Message: bizErr.Message,
				})
				return
			}
		}
	}
}
