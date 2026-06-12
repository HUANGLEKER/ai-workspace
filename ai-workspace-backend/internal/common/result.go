package common

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

// 对应 Spring Boot workspace-common 的 Result<T> / ResultCode
type Result[T any] struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
	Data    T      `json:"data,omitempty"`
}

const (
	CodeOK              = 200
	CodeBadRequest      = 400
	CodeUnauth          = 401
	CodeForbidden       = 403
	CodeNotFound        = 404
	CodeTooManyRequests = 429
	CodeServerError     = 500
)

func OK(c *gin.Context, data any) {
	c.JSON(http.StatusOK, Result[any]{Code: CodeOK, Message: "success", Data: data})
}

func OKMsg(c *gin.Context, msg string) {
	c.JSON(http.StatusOK, Result[any]{Code: CodeOK, Message: msg})
}

func Fail(c *gin.Context, code int, msg string) {
	c.JSON(http.StatusOK, Result[any]{Code: code, Message: msg})
}

func BadRequest(c *gin.Context, msg string) {
	Fail(c, CodeBadRequest, msg)
}

func Unauthorized(c *gin.Context) {
	Fail(c, CodeUnauth, "未登录或 Token 已过期")
}

func Forbidden(c *gin.Context) {
	Fail(c, CodeForbidden, "权限不足")
}

func ServerError(c *gin.Context, msg string) {
	Fail(c, CodeServerError, msg)
}
