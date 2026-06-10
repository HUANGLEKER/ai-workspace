package service

import (
	"context"
	"fmt"
	"net/http"
	"runtime"
	"time"

	"github.com/shirou/gopsutil/v3/cpu"
	"github.com/shirou/gopsutil/v3/disk"
	"github.com/shirou/gopsutil/v3/mem"

	"github.com/aiworkspace/backend/internal/config"
	"github.com/aiworkspace/backend/pkg/fastapi"
	minioPkg "github.com/aiworkspace/backend/pkg/minio"
	redisPkg "github.com/aiworkspace/backend/pkg/redis"
)

// MonitorSvc 是系统监控服务全局单例
var MonitorSvc = &monitorService{}

type monitorService struct{}

// ServerInfo 服务器运行时指标快照，对应 Spring Boot ServerInfoVO
type ServerInfo struct {
	CPU    CPUInfo    `json:"cpu"`
	Memory MemInfo    `json:"memory"`
	GoRT   GoRTInfo   `json:"runtime"`
	OS     OSInfo     `json:"os"`
	Disks  []DiskInfo `json:"disks"`
}

type CPUInfo struct {
	Cores           int     `json:"cores"`
	UsedPercent     float64 `json:"usedPercent"`
	ProcUsedPercent float64 `json:"procUsedPercent"`
}

type MemInfo struct {
	Total       uint64  `json:"total"`
	Used        uint64  `json:"used"`
	UsedPercent float64 `json:"usedPercent"`
}

type GoRTInfo struct {
	Version      string  `json:"version"`
	NumGoroutine int     `json:"numGoroutine"`
	HeapAlloc    uint64  `json:"heapAlloc"`
	HeapSys      uint64  `json:"heapSys"`
	HeapInuse    uint64  `json:"heapInuse"`
	UsedPercent  float64 `json:"usedPercent"`
}

type OSInfo struct {
	GOOS   string `json:"goos"`
	GOARCH string `json:"goarch"`
}

type DiskInfo struct {
	Path        string  `json:"path"`
	Total       uint64  `json:"total"`
	Used        uint64  `json:"used"`
	UsedPercent float64 `json:"usedPercent"`
}

// ServiceHealth 单个依赖服务的健康检查结果
type ServiceHealth struct {
	Name    string `json:"name"`
	Target  string `json:"target"`
	Status  string `json:"status"`  // "UP" | "DOWN"
	Latency int64  `json:"latency"` // 毫秒
	Message string `json:"message"`
}

// GetServerInfo 采集服务器运行时指标，通过 gopsutil 跨平台获取系统级数据
func (s *monitorService) GetServerInfo() ServerInfo {
	info := ServerInfo{}

	if percents, err := cpu.Percent(time.Second, false); err == nil && len(percents) > 0 {
		info.CPU.UsedPercent = round2(percents[0])
	}
	if cnt, err := cpu.Counts(true); err == nil {
		info.CPU.Cores = cnt
	}

	if vmStat, err := mem.VirtualMemory(); err == nil {
		info.Memory.Total = vmStat.Total
		info.Memory.Used = vmStat.Used
		info.Memory.UsedPercent = round2(vmStat.UsedPercent)
	}

	var ms runtime.MemStats
	runtime.ReadMemStats(&ms)
	info.GoRT = GoRTInfo{
		Version:      runtime.Version(),
		NumGoroutine: runtime.NumGoroutine(),
		HeapAlloc:    ms.HeapAlloc,
		HeapSys:      ms.HeapSys,
		HeapInuse:    ms.HeapInuse,
		UsedPercent:  round2(float64(ms.HeapAlloc) / float64(ms.HeapSys) * 100),
	}

	info.OS = OSInfo{GOOS: runtime.GOOS, GOARCH: runtime.GOARCH}

	if parts, err := disk.Partitions(false); err == nil {
		for _, p := range parts {
			if usage, err := disk.Usage(p.Mountpoint); err == nil && usage.Total > 0 {
				info.Disks = append(info.Disks, DiskInfo{
					Path:        p.Mountpoint,
					Total:       usage.Total,
					Used:        usage.Used,
					UsedPercent: round2(usage.UsedPercent),
				})
			}
		}
	}

	return info
}

// GetServiceHealth 探测 Redis、FastAPI、MinIO 的连通性与延迟
func (s *monitorService) GetServiceHealth() []ServiceHealth {
	cfg := config.Global
	return []ServiceHealth{
		checkRedis(),
		checkHTTP("FastAPI", fastapi.Client.BaseURL()+"/health"),
		checkHTTP("MinIO", fmt.Sprintf("http://%s/minio/health/live", cfg.MinIO.Endpoint)),
	}
}

// checkRedis 用 PING 命令探测 Redis 健康状态
func checkRedis() ServiceHealth {
	start := time.Now()
	err := redisPkg.Client.Ping(context.Background()).Err()
	latency := time.Since(start).Milliseconds()
	if err != nil {
		return ServiceHealth{Name: "Redis", Target: config.Global.Redis.Addr, Status: "DOWN", Latency: latency, Message: err.Error()}
	}
	return ServiceHealth{Name: "Redis", Target: config.Global.Redis.Addr, Status: "UP", Latency: latency}
}

// checkHTTP 以 GET 请求探测 HTTP 服务健康状态，超时 2 秒
func checkHTTP(name, url string) ServiceHealth {
	start := time.Now()
	client := &http.Client{Timeout: 2 * time.Second}
	resp, err := client.Get(url)
	latency := time.Since(start).Milliseconds()
	if err != nil {
		return ServiceHealth{Name: name, Target: url, Status: "DOWN", Latency: latency, Message: err.Error()}
	}
	defer resp.Body.Close()
	_ = minioPkg.Client
	if resp.StatusCode >= 200 && resp.StatusCode < 400 {
		return ServiceHealth{Name: name, Target: url, Status: "UP", Latency: latency, Message: fmt.Sprintf("HTTP %d", resp.StatusCode)}
	}
	return ServiceHealth{Name: name, Target: url, Status: "DOWN", Latency: latency, Message: fmt.Sprintf("HTTP %d", resp.StatusCode)}
}

func round2(v float64) float64 {
	return float64(int(v*100)) / 100
}
