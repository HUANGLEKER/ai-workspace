// Package router 负责全局中间件挂载与路由分组注册。
// 安全分层：public（无需 JWT）→ auth（需登录）→ admin（需登录 + ADMIN 角色）
package router

import (
	"github.com/gin-gonic/gin"
	"go.opentelemetry.io/contrib/instrumentation/github.com/gin-gonic/gin/otelgin"

	"github.com/aiworkspace/backend/internal/middleware"
)

// Setup 注册所有路由，对应 Spring Boot 的各 Controller + SecurityConfig 的路由规则。
func Setup(r *gin.Engine) {
	// 全局中间件：顺序很重要——Recovery 必须最先，确保 panic 能被捕获
	r.Use(middleware.Recovery())
	r.Use(middleware.CORS())
	// OpenTelemetry：为每个请求起 span（或延续上游 traceparent），续接前端→网关→FastAPI 链路。
	// 未配置追踪端点时全局为 no-op TracerProvider，开销可忽略。置于鉴权/日志前以覆盖全程。
	r.Use(otelgin.Middleware("ai-workspace-backend"))
	r.Use(middleware.RequestID()) // 必须在 RequestLogger 之前注入
	r.Use(middleware.RequestLogger())

	// 容器/负载均衡健康检查端点（无鉴权，不含任何业务信息）
	r.GET("/health", func(c *gin.Context) {
		c.JSON(200, gin.H{"status": "UP"})
	})

	api := r.Group("/api")

	// ── 公开接口（无需 JWT）──────────────────────────────────────────
	// 仅 login/logout；/auth/info 内部已挂 JWTAuth（在 registerAuthRoutes 中单独处理）
	registerAuthRoutes(api)

	// ── 需要登录的接口 ───────────────────────────────────────────────
	auth := api.Group("", middleware.JWTAuth())
	registerChatRoutes(auth)
	registerKBRoutes(auth)
	registerFileRoutes(auth)
	registerAgentRoutes(auth)
	registerWorkflowRoutes(auth)
	registerPromptRoutes(auth)
	registerToolRoutes(auth)
	registerMCPRoutes(auth)
	registerDashboardRoutes(auth)
	// User 路由：已在 registerUserRoutes 内部附加 AdminRequired
	registerUserRoutes(auth)

	// ── 仅管理员接口 ─────────────────────────────────────────────────
	admin := api.Group("", middleware.JWTAuth(), middleware.AdminRequired())
	registerMonitorRoutes(admin)
	registerJobRoutes(admin)
}
