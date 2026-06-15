# CLAUDE.md —— Go 后端（ai-workspace-backend）

> 本文件仅覆盖 Go 后端子项目。跨服务约定、整体架构见仓库根目录 `CLAUDE.md`。

## 角色

Go 1.23 + Gin + GORM。负责**鉴权 / RBAC / 业务逻辑 / 资源归属**；所有 LLM 交互（流式、嵌入、RAG、agent、工作流）都转发给 FastAPI AI 服务，本层经 `pkg/fastapi/` 统一客户端与之 HTTP 通信。**后端不读 LLM，FastAPI 不读 MySQL。**

```
Vue3 (3000) → Go/Gin (8080) → FastAPI (8001) → [Redis, ChromaDB, MinIO] → LLM
```

## 常用命令

```bash
go mod tidy                  # 同步依赖（首次或依赖变更后）
make run                     # = go run ./cmd/server -config config.yaml
make build                   # 编译为 bin/ai-workspace-backend
make swag                    # 重新生成 Swagger 文档（需 swag CLI）
go vet ./...                 # 静态检查
gofmt -l -w .                # 格式化
go test ./...                # 单元测试（service 层已有覆盖）
```

配置：`config.yaml`（端口、DSN、Redis、JWT、FastAPI、MinIO、日志、CORS、ratelimit、tracing、security、upload）。容器内用 `config.docker.yaml`。默认凭证：MySQL `root/123456`，管理员 `admin/123456`（首登强制改密）。

## 目录结构（`internal/`）

- `handler/` —— Gin handler，对应各业务模块（auth/chat/kb/rag/file/agent/workflow/job/prompt/tool/mcp/user/monitor/dashboard）；`sse.go` 为 SSE 透传
- `service/` —— 业务逻辑层
- `model/` —— GORM 模型（软删除走 `gorm.io/plugin/soft_delete` **milli 模式**）
- `repository/` —— DAO 层：泛型 `OwnedRepository[T]`（`owned.go`）封装用户私有资源 CRUD 与归属语义
- `router/` —— `router.go` 分层 public → auth → admin；`register.go` 注册各模块
- `middleware/` —— JWT、CORS、RequestLogger、Recovery、AdminRequired、RateLimit、RequestID
- `config/` —— Viper 配置加载，全局 `config.Global`
- `scheduler/` —— 动态 cron（`manager.go` 管 `robfig/cron/v3`，`handler.go` 注册 JobHandler）
- `common/` —— 标准响应包装（`result.go`）、分页（`page.go`）、上传校验（`upload.go`）
- `pkg/` —— 基础设施客户端：`database/`(GORM)、`redis/`、`minio/`、`logger/`(zap+lumberjack)、`fastapi/`(FastAPI HTTP 客户端)、`tracing/`(OpenTelemetry)、`crypto/`(AES-256-GCM)

## 资源归属（强约束，防 IDOR）

每个 list/get/update/delete 路径都必须按调用者 user id 校验归属。归属列名不一致：

- KB/文档/Agent/Workflow/Prompt/Tool/MCP：`create_by`
- Chat / RAG 会话/消息：`user_id`
- 文件：`upload_by`

**唯一强制入口**是 `service/owned.go` 的泛型 helper：单条校验用 `getOwnedResource[T]`（404/403 三态），列表/统计过滤用 `db.Scopes(ownedScope[T](userID))`。列名由模型实现 `model.Owned` 接口声明（`model/owned.go`），嵌入 `UserOwnedModel` 即自动获得 `create_by` 归属。**禁止在 service 层手写 `Where("create_by = ?")` 等字面量归属条件。**

## RBAC / 鉴权

JWT Claims：`userID`、`username`、`roles`（`[]string`，角色码带 `ROLE_` 前缀）。中间件解析后注入 `gin.Context`：`c.GetInt64(middleware.CtxUserID)`、`c.GetStringSlice(middleware.CtxRoles)`。`middleware.AdminRequired()` 检查是否含 `ROLE_ADMIN`。

多用户加固：`chat_model.api_key` 经 AES-256-GCM 加密落库（`pkg/crypto`，密钥取 `config.security.secret_key`，空则回退 `jwt.secret`；密文带 `enc:v1:` 前缀，对历史明文向后兼容）；CORS 由 `config.cors.allowed_origins` 控制；限流分级 `ratelimit.llm_per_minute` / `llm_per_minute_admin`；`sys_user.must_change_pwd` 驱动前端强制改密框。

## API 约定

所有端点以 `/api/` 为前缀，标准响应包装（`code`/`message`/`data`）来自 `common/result.go`。SSE 端点（`/api/chat/send`、`/api/rag/chat`）将 FastAPI 的 SSE 流透传到浏览器。出站调用 FastAPI 经 `pkg/fastapi/` 统一客户端（base URL/超时来自 `config.yaml` 的 `fastapi.*`）。完整端点清单见根 `CLAUDE.md`。

## 嵌入管道（Go 侧职责）

文档上传后异步（goroutine）调 FastAPI `POST /embedding/build`：插入 `kb_chunk_task`(RUNNING) → 置 `kb_document.status=PROCESSING` → 调用 → 成功置 SUCCESS/DONE，失败置 FAILED 写 `error_msg`。状态自愈：启动时 `RecoverInterruptedTasks` 重置遗留 RUNNING/PROCESSING；运行期 cron `embeddingReconcileJob`（`KBService.ReconcileStuck`，默认每 10 分钟/阈值 10 分钟）收敛卡死文档。

## 数据库维护约定

任何表结构变更**必须同步更新** `ai-workspace/sql/init.sql`（21 张表 DDL + 种子）。可用 `docker exec ai-workspace-mysql mysqldump -uroot -p123456 --no-data ai_workspace` 重导出。所有表用 `BIGINT AUTO_INCREMENT` 主键、`deleted BIGINT` 软删除、`utf8mb4`。时间戳由 GORM `AutoCreateTime`/`AutoUpdateTime` 填充（`sys_job_log` 例外，仅 `create_time`、物理删除）。
