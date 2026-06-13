package handler

import (
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
)

// prepareSSE 设置 SSE 响应头，并清除该连接的写超时。
//
// http.Server 的 WriteTimeout 是绝对值，会在固定时长后打断响应写入——对普通接口
// 是合理保护，但会截断长 SSE 流（如 agent 多轮工具调用）。这里用 ResponseController
// 仅对当前流式连接取消写截止时间，长流改由请求 ctx（浏览器断连即取消）与
// FastAPI 侧 keepalive 帧共同管理；普通接口仍受 server WriteTimeout 保护。
func prepareSSE(c *gin.Context) {
	c.Header("Content-Type", "text/event-stream")
	c.Header("Cache-Control", "no-cache")
	c.Header("X-Accel-Buffering", "no")

	rc := http.NewResponseController(c.Writer)
	// 零值 time.Time 表示清除写截止时间（不超时）
	if err := rc.SetWriteDeadline(time.Time{}); err != nil {
		// 某些 ResponseWriter 包装层不支持时仅告警，不影响流式（仍受 WriteTimeout 限制）
		zap.L().Warn("清除 SSE 写超时失败", zap.Error(err))
	}
}
