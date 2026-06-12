// deps.go 定义 service 层的依赖接口与单例组装。
//
// P1-1 去全局化：service 不再在方法内直接引用 database.DB / fastapi.Client /
// minio 包级函数，而是持有构造时注入的依赖。生产环境由 Init() 在 main 中统一
// 组装（在 MySQL/FastAPI/MinIO 初始化完成之后调用）；测试中以 sqlite 内存库与
// 假实现构造，无需任何外部基础设施。
//
// 渐进迁移：目前已注入化 ChatSvc / KBSvc，其余 service 仍为旧式全局单例，
// 后续逐模块迁移。
package service

import (
	"context"
	"io"
	"time"

	"github.com/aiworkspace/backend/pkg/database"
	"github.com/aiworkspace/backend/pkg/fastapi"
	minioPkg "github.com/aiworkspace/backend/pkg/minio"
)

// EmbeddingCaller 是 KB 服务对 FastAPI 客户端的最小依赖面（嵌入构建/向量删除）。
type EmbeddingCaller interface {
	Send(ctx context.Context, path string, body any, timeout time.Duration) error
	SendMethod(ctx context.Context, method, path string, body any, timeout time.Duration) error
}

// ObjectStore 是 KB 服务对对象存储的最小依赖面。
type ObjectStore interface {
	Upload(ctx context.Context, objectName string, reader io.Reader, size int64, contentType string) error
	Delete(ctx context.Context, objectName string) error
}

// minioStore 将 pkg/minio 的包级函数适配为 ObjectStore 接口。
type minioStore struct{}

func (minioStore) Upload(ctx context.Context, objectName string, reader io.Reader, size int64, contentType string) error {
	return minioPkg.Upload(ctx, objectName, reader, size, contentType)
}

func (minioStore) Delete(ctx context.Context, objectName string) error {
	return minioPkg.Delete(ctx, objectName)
}

// Init 组装已注入化的 service 单例，必须在 database/fastapi/minio 初始化之后调用。
func Init() {
	ChatSvc = NewChatService(database.DB)
	KBSvc = NewKBService(database.DB, fastapi.Client, minioStore{})
}
