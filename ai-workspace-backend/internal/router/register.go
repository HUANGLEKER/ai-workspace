package router

import (
	"github.com/gin-gonic/gin"

	"github.com/aiworkspace/backend/internal/handler"
	"github.com/aiworkspace/backend/internal/middleware"
)

func registerAuthRoutes(rg *gin.RouterGroup) {
	g := rg.Group("/auth")
	// 登录/登出无需 JWT
	g.POST("/login", handler.Login)
	g.POST("/logout", handler.Logout)
	// /info 需要登录才能查询
	g.GET("/info", middleware.JWTAuth(), handler.GetAuthInfo)
	// 修改密码
	g.PUT("/password", middleware.JWTAuth(), handler.UpdatePassword)
}

func registerUserRoutes(rg *gin.RouterGroup) {
	// 用户管理仅管理员可操作，通过 AdminRequired 中间件保护
	g := rg.Group("/user", middleware.AdminRequired())
	g.GET("/page", handler.ListUsers)
	g.POST("/add", handler.AddUser)
	g.PUT("/update", handler.UpdateUser)
	g.DELETE("/delete/:id", handler.DeleteUser)
	g.PUT("/status", handler.UpdateUserStatus)
}

func registerChatRoutes(rg *gin.RouterGroup) {
	g := rg.Group("/chat")
	// LLM 直通端点挂限流，防止刷请求造成 LLM 账单失控
	g.POST("/send", middleware.LLMRateLimit(), handler.ChatSend)
	g.GET("/session/list", handler.ListSessions)
	g.POST("/session/add", handler.AddSession)
	g.PUT("/session/:id", handler.RenameSession)
	g.DELETE("/session/:id", handler.DeleteSession)
	g.GET("/session/:id/messages", handler.ListMessages)

	// LLM 模型配置：读取所有人可用，增删改仅管理员
	g.GET("/model/list", handler.ListChatModels)
	admin := g.Group("/model", middleware.AdminRequired())
	admin.GET("/page", handler.PageChatModels)
	admin.POST("/add", handler.AddChatModel)
	admin.PUT("/update", handler.UpdateChatModel)
	admin.PUT("/status", handler.UpdateChatModelStatus)
	admin.DELETE("/:id", handler.DeleteChatModel)
}

func registerKBRoutes(rg *gin.RouterGroup) {
	g := rg.Group("/kb")
	g.GET("/list", handler.ListKBs)
	g.POST("/add", handler.AddKB)
	g.PUT("/update", handler.UpdateKB)
	g.DELETE("/delete/:id", handler.DeleteKB)

	doc := rg.Group("/document")
	doc.GET("/list", handler.ListDocuments)
	doc.POST("/upload", handler.UploadDocument)
	doc.DELETE("/delete/:id", handler.DeleteDocument)

	rag := rg.Group("/rag")
	rag.POST("/chat", middleware.LLMRateLimit(), handler.RAGChat)
	rag.POST("/rebuild", handler.RAGRebuild)
}

func registerFileRoutes(rg *gin.RouterGroup) {
	g := rg.Group("/file")
	g.GET("/list", handler.ListFiles)
	g.POST("/upload", handler.UploadFile)
	g.DELETE("/delete/:id", handler.DeleteFile)
	g.GET("/presign/:id", handler.PresignFile)
}

func registerAgentRoutes(rg *gin.RouterGroup) {
	g := rg.Group("/agent")
	g.GET("/list", handler.ListAgents)
	g.GET("/:id", handler.GetAgent)
	g.POST("/add", handler.AddAgent)
	g.PUT("/update", handler.UpdateAgent)
	g.DELETE("/delete/:id", handler.DeleteAgent)
	g.POST("/:id/run", middleware.LLMRateLimit(), handler.RunAgent)
}

func registerWorkflowRoutes(rg *gin.RouterGroup) {
	g := rg.Group("/workflow")
	g.GET("/list", handler.ListWorkflows)
	g.GET("/:id", handler.GetWorkflow)
	g.POST("/add", handler.AddWorkflow)
	g.PUT("/update", handler.UpdateWorkflow)
	g.DELETE("/delete/:id", handler.DeleteWorkflow)
	g.POST("/:id/run", middleware.LLMRateLimit(), handler.RunWorkflow)
}

func registerPromptRoutes(rg *gin.RouterGroup) {
	g := rg.Group("/prompt")
	g.GET("/list", handler.ListPrompts)
	g.GET("/:id", handler.GetPrompt)
	g.POST("/add", handler.AddPrompt)
	g.PUT("/update", handler.UpdatePrompt)
	g.DELETE("/delete/:id", handler.DeletePrompt)
}

func registerToolRoutes(rg *gin.RouterGroup) {
	g := rg.Group("/tool")
	g.GET("/list", handler.ListTools)
	g.GET("/:id", handler.GetTool)
	g.POST("/add", handler.AddTool)
	g.PUT("/update", handler.UpdateTool)
	g.DELETE("/delete/:id", handler.DeleteTool)
}

func registerMCPRoutes(rg *gin.RouterGroup) {
	g := rg.Group("/mcp")
	g.GET("/list", handler.ListMCPs)
	g.GET("/:id", handler.GetMCP)
	g.POST("/add", handler.AddMCP)
	g.PUT("/update", handler.UpdateMCP)
	g.DELETE("/delete/:id", handler.DeleteMCP)
	g.POST("/test/:id", handler.TestMCP)
}

func registerDashboardRoutes(rg *gin.RouterGroup) {
	rg.GET("/dashboard/stats", handler.DashboardStats)
}

func registerMonitorRoutes(rg *gin.RouterGroup) {
	g := rg.Group("/monitor")
	g.GET("/server", handler.MonitorServer)
	g.GET("/health", handler.MonitorHealth)
}

func registerJobRoutes(rg *gin.RouterGroup) {
	g := rg.Group("/job")
	g.GET("/page", handler.ListJobs)
	g.GET("/handlers", handler.ListJobHandlers)
	g.POST("/add", handler.AddJob)
	g.PUT("/update", handler.UpdateJob)
	g.DELETE("/delete/:id", handler.DeleteJob)
	g.PUT("/status", handler.UpdateJobStatus)
	g.POST("/run/:id", handler.RunJob)

	log := rg.Group("/job/log")
	log.GET("/page", handler.ListJobLogs)
	log.DELETE("/clean", handler.CleanJobLogs)
}
