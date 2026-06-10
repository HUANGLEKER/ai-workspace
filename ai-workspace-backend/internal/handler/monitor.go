package handler

import (
	"github.com/gin-gonic/gin"

	"github.com/aiworkspace/backend/internal/common"
	"github.com/aiworkspace/backend/internal/service"
)

// MonitorServer GET /api/monitor/server — 仅管理员，采集服务器运行时指标快照
//
// 指标通过 gopsutil 跨平台获取，包含 CPU 使用率、物理内存、Go 运行时（等价 JVM 堆）、
// OS 信息与各磁盘分区使用情况，按请求实时采样，不持久化。
func MonitorServer(c *gin.Context) {
	common.OK(c, service.MonitorSvc.GetServerInfo())
}

// MonitorHealth GET /api/monitor/health — 仅管理员，探测依赖服务健康状态
//
// 探测 Redis（PING）、FastAPI（GET /health）、MinIO（GET /minio/health/live），
// 每个探针超时 2 秒，结果含 status(UP/DOWN)、延迟(ms)和错误信息。
func MonitorHealth(c *gin.Context) {
	common.OK(c, service.MonitorSvc.GetServiceHealth())
}
