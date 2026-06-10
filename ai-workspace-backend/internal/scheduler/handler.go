// Package scheduler 提供动态 cron 任务调度能力。
// 对应 Spring Boot workspace-job 的 JobHandler + JobHandlerRegistry + SampleJobHandler。
package scheduler

import (
	"fmt"
	"sync"
	"time"

	"go.uber.org/zap"
)

// JobHandler 定义可被调度器执行的任务接口。
// 实现此接口后通过 Register 注册到全局注册表，SysJob.InvokeTarget 引用注册名。
type JobHandler interface {
	// Execute 执行任务逻辑；params 来自 sys_job.job_params，返回错误会被记录到 sys_job_log
	Execute(params string) error
}

// Registry 是线程安全的 JobHandler 注册表单例
var Registry = newRegistry()

type handlerRegistry struct {
	mu       sync.RWMutex
	handlers map[string]JobHandler
}

func newRegistry() *handlerRegistry {
	return &handlerRegistry{handlers: make(map[string]JobHandler)}
}

// Register 注册一个 JobHandler，name 即 SysJob.InvokeTarget 中引用的 bean 名。
// 应在 main.go 初始化阶段调用，非线程安全写入（init 阶段单线程即可）。
func (r *handlerRegistry) Register(name string, h JobHandler) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.handlers[name] = h
}

// Get 按名称查找已注册的处理器，不存在时返回 (nil, false)
func (r *handlerRegistry) Get(name string) (JobHandler, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	h, ok := r.handlers[name]
	return h, ok
}

// Names 返回所有已注册处理器的名称列表，供 /api/job/handlers 接口使用
func (r *handlerRegistry) Names() []string {
	r.mu.RLock()
	defer r.mu.RUnlock()
	names := make([]string, 0, len(r.handlers))
	for name := range r.handlers {
		names = append(names, name)
	}
	return names
}

// ─── 内置处理器 ────────────────────────────────────────────────────

// SampleJobHandler 内置示例处理器，对应 Spring Boot 的 SampleJobHandler。
// 每次触发时仅记录一条日志，用于验证调度器正常工作。
type SampleJobHandler struct{}

func (h *SampleJobHandler) Execute(params string) error {
	zap.L().Info("SampleJobHandler 执行", zap.String("params", params), zap.String("time", time.Now().Format(time.DateTime)))
	fmt.Printf("[SampleJob] %s params=%s\n", time.Now().Format(time.DateTime), params)
	return nil
}
