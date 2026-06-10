package common

import "fmt"

// BusinessError 对应 Spring Boot 的 BusinessException，携带业务错误码
type BusinessError struct {
	Code    int
	Message string
}

func (e *BusinessError) Error() string {
	return fmt.Sprintf("business error [%d]: %s", e.Code, e.Message)
}

func NewBizError(code int, msg string) *BusinessError {
	return &BusinessError{Code: code, Message: msg}
}

func ErrNotFound(resource string) *BusinessError {
	return &BusinessError{Code: CodeNotFound, Message: resource + " 不存在"}
}

func ErrForbidden() *BusinessError {
	return &BusinessError{Code: CodeForbidden, Message: "无权访问该资源"}
}
