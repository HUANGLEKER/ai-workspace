// deps.go 定义 service 层的依赖接口与单例组装。
//
// P1-1 去全局化：service 不再在方法内直接引用 database.DB / fastapi.Client /
// minio 包级函数，而是持有构造时注入的依赖。生产环境由 Init() 在 main 中统一
// 组装（在 MySQL/FastAPI/MinIO 初始化完成之后调用）；测试中以 sqlite 内存库与
// 假实现构造，无需任何外部基础设施。
//
// 全部 12 个 service 均已注入化，由 Init() 统一组装。
package service

import (
	"context"
	"encoding/json"
	"io"
	"time"

	"github.com/aiworkspace/backend/pkg/database"
	"github.com/aiworkspace/backend/pkg/fastapi"
	minioPkg "github.com/aiworkspace/backend/pkg/minio"
	redisPkg "github.com/aiworkspace/backend/pkg/redis"
)

// EmbeddingCaller 是 KB 服务对 FastAPI 客户端的最小依赖面（嵌入构建/向量删除）。
type EmbeddingCaller interface {
	Send(ctx context.Context, path string, body any, timeout time.Duration) error
	SendMethod(ctx context.Context, method, path string, body any, timeout time.Duration) error
}

// RunCaller 是 Agent/Workflow 服务对 FastAPI 客户端的最小依赖面（运行并取回结果）。
type RunCaller interface {
	PostForData(ctx context.Context, path string, body any) (json.RawMessage, error)
}

// ObjectStore 是 KB/文件服务对对象存储的最小依赖面。
type ObjectStore interface {
	Upload(ctx context.Context, objectName string, reader io.Reader, size int64, contentType string) error
	Delete(ctx context.Context, objectName string) error
	PresignedURL(ctx context.Context, objectName string, expiry time.Duration) (string, error)
}

// minioStore 将 pkg/minio 的包级函数适配为 ObjectStore 接口。
type minioStore struct{}

func (minioStore) Upload(ctx context.Context, objectName string, reader io.Reader, size int64, contentType string) error {
	return minioPkg.Upload(ctx, objectName, reader, size, contentType)
}

func (minioStore) Delete(ctx context.Context, objectName string) error {
	return minioPkg.Delete(ctx, objectName)
}

func (minioStore) PresignedURL(ctx context.Context, objectName string, expiry time.Duration) (string, error) {
	return minioPkg.PresignedURL(ctx, objectName, expiry)
}

// Init 组装全部 service 单例，必须在 database/redis/fastapi/minio 初始化之后调用。
func Init() {
	db := database.DB
	ChatSvc = NewChatService(db, fastapi.Client)
	KBSvc = NewKBService(db, fastapi.Client, minioStore{})
	AgentSvc = NewAgentService(db, fastapi.Client)
	WorkflowSvc = NewWorkflowService(db, fastapi.Client)
	PromptSvc = NewPromptService(db)
	ToolSvc = NewToolService(db)
	MCPSvc = NewMCPService(db)
	UserSvc = NewUserService(db)
	JobSvc = NewJobService(db)
	DashboardSvc = NewDashboardService(db)
	UsageSvc = NewUsageService(db)
	FileSvc = NewFileService(db, minioStore{})
	MonitorSvc = NewMonitorService(fastapi.Client.BaseURL(), redisPkg.Client)
}
