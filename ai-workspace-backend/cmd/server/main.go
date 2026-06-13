// main.go 是 AI Workspace Go 后端的启动入口。
// 按依赖顺序初始化：配置 → 日志 → MySQL → Redis → MinIO → FastAPI → 调度器 → HTTP 服务器。
package main

import (
	"context"
	"flag"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gin-gonic/gin"
	"go.uber.org/zap"

	"github.com/aiworkspace/backend/internal/config"
	"github.com/aiworkspace/backend/internal/router"
	"github.com/aiworkspace/backend/internal/scheduler"
	"github.com/aiworkspace/backend/internal/service"
	"github.com/aiworkspace/backend/pkg/crypto"
	"github.com/aiworkspace/backend/pkg/database"
	"github.com/aiworkspace/backend/pkg/fastapi"
	"github.com/aiworkspace/backend/pkg/logger"
	minioPkg "github.com/aiworkspace/backend/pkg/minio"
	redisPkg "github.com/aiworkspace/backend/pkg/redis"
)

func main() {
	cfgFile := flag.String("config", "config.yaml", "配置文件路径")
	flag.Parse()

	// ── 1. 加载配置 ─────────────────────────────────────────────────
	if err := config.Load(*cfgFile); err != nil {
		fmt.Fprintf(os.Stderr, "加载配置失败: %v\n", err)
		os.Exit(1)
	}

	// ── 2. 初始化日志 ────────────────────────────────────────────────
	if err := logger.Init(config.Global.Log); err != nil {
		fmt.Fprintf(os.Stderr, "初始化日志失败: %v\n", err)
		os.Exit(1)
	}
	defer logger.Sync()

	// ── 2.5 初始化敏感字段加密（P3-6）——密钥空时回退 JWT secret ──────
	secret := config.Global.Security.SecretKey
	if secret == "" {
		secret = config.Global.JWT.Secret
	}
	if err := crypto.Init(secret); err != nil {
		zap.L().Fatal("初始化加密失败", zap.Error(err))
	}

	// ── 3. 初始化 MySQL ──────────────────────────────────────────────
	if err := database.Init(config.Global.Database); err != nil {
		zap.L().Fatal("初始化 MySQL 失败", zap.Error(err))
	}
	zap.L().Info("MySQL 连接成功")

	// ── 4. 初始化 Redis ──────────────────────────────────────────────
	if err := redisPkg.Init(config.Global.Redis); err != nil {
		zap.L().Fatal("初始化 Redis 失败", zap.Error(err))
	}
	zap.L().Info("Redis 连接成功")

	// ── 5. 初始化 MinIO ──────────────────────────────────────────────
	if err := minioPkg.Init(config.Global.MinIO); err != nil {
		zap.L().Fatal("初始化 MinIO 失败", zap.Error(err))
	}
	zap.L().Info("MinIO 连接成功")

	// ── 6. 初始化 FastAPI 客户端 ─────────────────────────────────────
	fastapi.Init(config.Global.FastAPI)
	zap.L().Info("FastAPI 客户端已初始化", zap.String("baseURL", config.Global.FastAPI.BaseURL))

	// ── 6.2 组装注入化的 service 单例（依赖 MySQL/FastAPI/MinIO 均已就绪）──
	service.Init()

	// ── 6.5 重置上次进程退出时中断的嵌入任务（RUNNING→FAILED）────────
	// 嵌入是裸 goroutine，重启即丢；不重置会留下永久 RUNNING 的僵尸记录
	if err := service.KBSvc.RecoverInterruptedTasks(); err != nil {
		zap.L().Warn("重置中断嵌入任务失败", zap.Error(err))
	}

	// ── 7. 注册 JobHandler 并启动调度器 ──────────────────────────────
	scheduler.Registry.Register("sampleJob", &scheduler.SampleJobHandler{})
	// 每日 token 用量聚合（P2-2）：cron 排程见 sys_job（invoke_target=usageDailyJob）
	scheduler.Registry.Register("usageDailyJob", &service.UsageJobHandler{})
	// 注入日志回调，解耦调度器与服务层（避免循环导入）
	scheduler.OnJobDone = service.JobSvc.WriteLog
	// 从数据库加载 status=0（运行中）的任务，重新加入调度器
	if err := service.JobSvc.LoadAndScheduleRunning(); err != nil {
		zap.L().Warn("加载定时任务失败", zap.Error(err))
	}
	scheduler.Manager.Start()
	defer scheduler.Manager.Stop()
	zap.L().Info("定时任务调度器已启动")

	// ── 8. 配置 Gin 并注册路由 ───────────────────────────────────────
	gin.SetMode(config.Global.Server.Mode)
	r := gin.New() // 不用 gin.Default()，所有中间件由 router.Setup 统一注册
	router.Setup(r)

	// ── 9. 启动 HTTP 服务（支持优雅退出）────────────────────────────
	srv := &http.Server{
		Addr:    fmt.Sprintf(":%d", config.Global.Server.Port),
		Handler: r,
		// ReadTimeout 限制请求头读取时间，WriteTimeout 给 SSE 流留足窗口
		ReadTimeout:  30 * time.Second,
		WriteTimeout: 180 * time.Second,
	}

	go func() {
		zap.L().Info("HTTP 服务启动", zap.String("addr", srv.Addr))
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			zap.L().Fatal("HTTP 服务启动失败", zap.Error(err))
		}
	}()

	// 等待中断信号后优雅关闭（10 秒内完成已接收请求）
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	zap.L().Info("收到退出信号，正在优雅关闭...")

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(ctx); err != nil {
		zap.L().Error("HTTP 服务关闭异常", zap.Error(err))
	}
	zap.L().Info("服务已退出")
}
