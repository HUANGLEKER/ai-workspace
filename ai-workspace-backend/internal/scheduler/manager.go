package scheduler

import (
	"fmt"
	"sync"
	"time"

	"github.com/robfig/cron/v3"
	"go.uber.org/zap"
)

// Manager 是全局调度器单例，封装 robfig/cron 提供动态增删任务的能力。
// 对应 Spring Boot 的 JobSchedulerManager + ThreadPoolTaskScheduler。
var Manager = newManager()

// OnJobDone 是每次任务执行完毕后的回调钩子，由 JobService 注入用于写 sys_job_log。
// 解耦调度器与持久层，保持 scheduler 包不依赖 service 包（避免循环导入）。
var OnJobDone func(jobID int64, jobName, invokeTarget, params string, status int8, msg string, costMs int64)

type jobManager struct {
	mu sync.Mutex
	c  *cron.Cron
	// entryMap 存储 jobID -> cron.EntryID 的映射，用于动态移除任务
	entryMap map[int64]cron.EntryID
}

func newManager() *jobManager {
	return &jobManager{
		// WithSeconds 启用 6 段 cron 表达式（秒 分 时 日 月 周），与 Spring Boot cron 格式保持一致
		c:        cron.New(cron.WithSeconds()),
		entryMap: make(map[int64]cron.EntryID),
	}
}

// Start 启动调度器后台 goroutine，应在所有任务加载完成后调用一次。
func (m *jobManager) Start() {
	m.c.Start()
	zap.L().Info("定时任务调度器已启动")
}

// Stop 优雅停止调度器，等待正在执行的任务完成后退出。
func (m *jobManager) Stop() {
	m.c.Stop()
	zap.L().Info("定时任务调度器已停止")
}

// Add 向调度器添加一个定时任务。
// jobID 用于后续的 Remove/Update 操作，handlerName 需已注册到 Registry。
func (m *jobManager) Add(jobID int64, jobName, invokeTarget, cronExpr, params string) error {
	handler, ok := Registry.Get(invokeTarget)
	if !ok {
		return fmt.Errorf("handler '%s' 未注册", invokeTarget)
	}

	m.mu.Lock()
	defer m.mu.Unlock()

	// 若同 ID 任务已存在则先移除，确保幂等
	if entryID, exists := m.entryMap[jobID]; exists {
		m.c.Remove(entryID)
	}

	entryID, err := m.c.AddFunc(cronExpr, func() {
		m.runJob(jobID, jobName, invokeTarget, params, handler)
	})
	if err != nil {
		return fmt.Errorf("添加 cron 任务失败（表达式: %s）: %w", cronExpr, err)
	}

	m.entryMap[jobID] = entryID
	zap.L().Info("定时任务已调度", zap.Int64("jobId", jobID), zap.String("cron", cronExpr))
	return nil
}

// Remove 从调度器移除指定任务，不影响数据库记录。
func (m *jobManager) Remove(jobID int64) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if entryID, ok := m.entryMap[jobID]; ok {
		m.c.Remove(entryID)
		delete(m.entryMap, jobID)
		zap.L().Info("定时任务已移除", zap.Int64("jobId", jobID))
	}
}

// RunOnce 立即触发一次任务执行（异步），不影响 cron 调度节奏。
func (m *jobManager) RunOnce(jobID int64, jobName, invokeTarget, params string) error {
	handler, ok := Registry.Get(invokeTarget)
	if !ok {
		return fmt.Errorf("handler '%s' 未注册", invokeTarget)
	}
	go m.runJob(jobID, jobName, invokeTarget, params, handler)
	return nil
}

// ValidateCron 校验 cron 表达式是否合法（6 段含秒），创建/更新时调用。
func ValidateCron(expr string) error {
	p := cron.NewParser(cron.Second | cron.Minute | cron.Hour | cron.Dom | cron.Month | cron.Dow)
	_, err := p.Parse(expr)
	return err
}

// runJob 执行任务并通过 OnJobDone 钩子记录结果。
func (m *jobManager) runJob(jobID int64, jobName, invokeTarget, params string, handler JobHandler) {
	start := time.Now()
	var (
		status int8
		msg    string
	)
	if err := handler.Execute(params); err != nil {
		status = 1 // 失败
		msg = err.Error()
		zap.L().Error("定时任务执行失败",
			zap.Int64("jobId", jobID),
			zap.String("handler", invokeTarget),
			zap.Error(err),
		)
	} else {
		status = 0 // 成功
		msg = "执行成功"
		zap.L().Info("定时任务执行完成", zap.Int64("jobId", jobID), zap.String("handler", invokeTarget))
	}

	if OnJobDone != nil {
		OnJobDone(jobID, jobName, invokeTarget, params, status, msg, time.Since(start).Milliseconds())
	}
}
