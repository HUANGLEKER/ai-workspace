# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

**AI Workspace v1.0** —— 一个集成了 Chat、知识库、RAG、提示词中心、工作流、Agent、MCP、工具中心、文件中心与仪表盘的个人 AI 平台。目标是作为个人 AI 中枢，替代 ChatGPT + Dify + OpenWebUI + Notion AI 的组合。

## 技术栈

| 层级 | 技术 |
|---|---|
| 前端 | Vue3 + TypeScript + TailwindCSS 4 + Radix Vue + Lucide（`ai-workspace-web/`） |
| 后端 | Go 1.23 + Gin + GORM（`ai-workspace-backend/`） |
| AI 服务 | FastAPI 0.115 + LangGraph + LangChain + OpenAI（`ai-service/`） |
| 数据库 | MySQL 8 |
| 缓存 | Redis 7（localhost:6379） |
| 向量库 | ChromaDB（localhost:8000） |
| 对象存储 | MinIO（localhost:9000） |

## 架构

三层架构：Vue3 前端 → Go/Gin 后端（REST API）→ FastAPI AI 服务。Go 层负责鉴权 / RBAC / 业务逻辑；FastAPI 负责所有 LLM 交互（流式、嵌入、RAG、agent、工作流）。后端通过 HTTP 与 FastAPI 通信。

```
Vue3 (3000) → Go/Gin (8080) → FastAPI (8001) → [Redis, ChromaDB, MinIO] → LLM
```

## 启动服务

### 前端

```bash
# 在 ai-workspace-web/ 下
npm install
npm run dev      # 开发服务器运行于 3000 端口，代理到 localhost:8080
npm run build    # 生产构建（先 vue-tsc 再 vite build）
npx vue-tsc --noEmit   # 仅类型检查，不输出文件
```

UI 栈已从 Element Plus 全面迁移为 **TailwindCSS 4（`@tailwindcss/vite` 插件 + `@tailwindcss/typography`）+ Radix Vue（headless 交互组件）+ lucide-vue-next（图标）**。规范：

- 禁止引入 Element Plus / 其他组件库图标集；禁止 `<style scoped>`、`::v-deep`、`!important`，所有样式用 Tailwind utility class。
- 统一组件库在 `src/components/ui`（AppButton/AppInput/AppDialog/AppTable/AppSelect/AppPagination 等，经 `index.ts` 出口统一引入）；`toast`（替代 ElMessage）与 `confirm`/`alertBox`（替代 ElMessageBox，Promise<boolean> 风格）也从该出口引入，渲染单例 `AppToaster`/`AppConfirm` 挂载在 `App.vue`（同时提供 Radix `TooltipProvider`）。
- 设计基调：zinc 色阶、`rounded-xl/2xl`、`border-zinc-200/80`、品牌色 `bg-zinc-900 text-white`、动效 `transition-all duration-200 ease-out`。
- 文件上传用 `AppUpload`（原生 fetch + FormData，手动注入 Authorization 头）。
- `build.rollupOptions.output.manualChunks` 仅强制拆分框架核心（`vue-vendor`）与较重的 markdown 栈（`markdown`，随 chat/RAG 懒加载）。

### Go 后端

```bash
# 在 ai-workspace-backend/ 下
go mod tidy                              # 同步依赖（首次或依赖变更后）
make run                                 # 等价于 go run ./cmd/server -config config.yaml
make build                               # 编译为 bin/ai-workspace-backend
make swag                                # 重新生成 Swagger 文档（需安装 swag CLI）
go vet ./...                             # 静态检查
gofmt -l -w .                           # 格式化所有 Go 文件
```

配置文件：`ai-workspace-backend/config.yaml`（服务端口、DSN、Redis、JWT、FastAPI、MinIO、日志）。  
默认凭证：MySQL `root / 123456`，管理员账号 `admin / 123456`。

Go 后端结构（`internal/`）：
- `handler/` —— Gin handler，对应各业务模块（auth/chat/kb/file/agent/workflow/job/prompt/tool/mcp/user/monitor/dashboard）
- `service/` —— 业务逻辑层
- `model/` —— GORM 模型（软删除通过 `gorm.io/plugin/soft_delete`）
- `router/router.go` —— 路由分层：`public`（无 JWT）→ `auth`（需登录）→ `admin`（需登录 + ADMIN 角色）
- `middleware/` —— JWT（`CtxUserID`/`CtxRoles` 注入 gin.Context）、CORS、RequestLogger、Recovery、AdminRequired
- `config/` —— Viper 配置加载，全局 `config.Global`
- `scheduler/` —— 动态 cron 调度器（`manager.go` 管理 `robfig/cron/v3` 实例，`handler.go` 注册 JobHandler）
- `common/` —— 标准响应包装（`result.go`）
- `pkg/` —— 基础设施客户端：`database/`（GORM）、`redis/`、`minio/`、`logger/`（zap + lumberjack）、`fastapi/`（FastAPI HTTP 客户端）

### FastAPI AI 服务

```bash
# 在 ai-service/ 下
cp .env.example .env          # 填入 LLM key 与服务配置（首次必做）
uv sync                       # 按 uv.lock 安装依赖（首次或依赖变更后）
uv add <pkg>                  # 新增依赖（会同步更新 uv.lock）
uv run python main.py         # 以 uvicorn 运行于 8001 端口，开启自动重载
```

`ai-service/` 未配置 linter；`pyproject.toml` 中无 `ruff`/`black` 等工具，如需格式化请手动安装后运行 `uv run ruff check .`。

依赖通过 `pyproject.toml` + `uv.lock` 管理。新增包用 `uv add <pkg>`。

关键 `.env` 变量：
- **Chat LLM**：`LLM_API_KEY`、`LLM_API_BASE`、`LLM_MODEL`、`LLM_TIMEOUT`、`LLM_MAX_RETRIES`
- **Embedding（独立）**：`EMBEDDING_API_KEY`、`EMBEDDING_API_BASE`、`EMBEDDING_MODEL`。DeepSeek 不提供嵌入端点，需单独配置其他 OpenAI 兼容提供商（推荐硅基流动 `BAAI/bge-m3`）。`EMBEDDING_API_KEY` / `EMBEDDING_API_BASE` 为空时自动回退使用 `LLM_*` 配置。
- **基础设施**：`REDIS_HOST`、`CHROMA_HOST`、`CHROMA_COLLECTION_PREFIX`、`MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`MINIO_BUCKET`

FastAPI 使用 Redis DB 1；Go 后端使用 DB 0。切换嵌入模型后必须重建知识库索引（旧向量与新模型不兼容）。

嵌入管道的临时文件已使用 `tempfile.gettempdir()`（跨平台），Windows 原生运行 AI 服务可用。

### 数据库

`docker-compose.infra.yml` 将 `ai-workspace/sql/init.sql` 挂载为 MySQL 初始化脚本（仅数据卷首次初始化时执行），包含全部 19 张表的 DDL 与种子数据（admin/123456、ROLE_ADMIN、默认 chat_model）。**维护约定：任何表结构变更必须同步更新 init.sql**（可用 `docker exec ai-workspace-mysql mysqldump -uroot -p123456 --no-data ai_workspace` 重新导出）。

### 容器化整栈运行（可选）

三个应用服务均有 Dockerfile，经 `docker-compose.app.yml` 编排（先起基础设施）：

```bash
docker-compose -f docker-compose.infra.yml up -d
docker-compose -f docker-compose.app.yml up -d --build   # 入口 http://localhost:3000
```

- 两个 compose 共享命名网络 `ai-workspace-net`；后端容器配置用 `ai-workspace-backend/config.docker.yaml`（主机名为 compose 服务名），AI 服务配置经环境变量注入（LLM key 从宿主环境透传）。
- 前端容器 nginx 反代 `/api` 到 backend（`nginx.conf` 已配 SSE 透传：`proxy_buffering off`）。
- 后端有公开 `GET /health`（容器健康检查用，无业务信息）。
- 注意：应用容器与本机开发进程端口冲突（3000/8080/8001），二者择一运行。
- CI（`.github/workflows/ci.yml`）：Go gofmt/vet/test/build、前端 vue-tsc/vitest/build、AI 服务 uv 冻结安装 + 导入冒烟。

### 本地基础设施（全部 Docker 化）

所有基础设施（MySQL、Redis、MinIO、ChromaDB）均通过 `docker-compose.infra.yml` 统一管理：

```bash
docker-compose -f docker-compose.infra.yml up -d
```

- MySQL 8.0（3306）：数据持久化到 `mysql-data` volume
- Redis 7（6379）：开启 AOF 持久化，数据持久化到 `redis-data` volume
- MinIO（9000 S3 API / 9011 控制台）：数据持久化到 `minio-data` volume
- ChromaDB（8000）：数据持久化到 `chroma-data` volume

## 测试

目前尚无测试套件。Go 后端与 AI 服务均无自动化测试配置。

## 模块结构

**FastAPI（`ai-service/app`）：**

- `chat/` —— LLM 调用、SSE 流式（`POST /chat`）
- `rag/` —— 向量检索 + 答案生成（`POST /rag/chat`）
- `embedding/` —— 文档切片、嵌入、写入 ChromaDB（`POST /embedding/build`、`DELETE /embedding/delete`）
- `agent/` —— 工具调用 agent（`POST /agent/run`）：绑定由 `HttpToolSpec` 构建的 HTTP 工具（经 `httpx` 执行），外加通过 `langchain-mcp-adapters`（`MultiServerMCPClient`）从 SSE MCP 服务器加载的工具，然后运行有界的 think→act 循环，返回答案与 `steps` 轨迹。MCP 导入做了保护，缺少该可选库时服务仍可运行。
- `workflow/` —— 基于 LangGraph 的引擎（`POST /workflow/run`）
- `llm/provider.py` —— LLM 提供方抽象（通过 LangChain 接 OpenAI）
- `vectorstore/chroma_client.py` —— ChromaDB HTTP 客户端
- `config/settings.py` —— 从 `.env` 加载的 Pydantic `BaseSettings`

**前端（`ai-workspace-web/src`）：**

- `api/` —— 各功能的 Axios HTTP 客户端模块（`sse.ts` 为共用 SSE 工具）
- `views/` —— 页面组件
- `components/ui/` —— 统一组件库（AppButton/AppInput/AppDialog… 经 `index.ts` 出口）
- `composables/` —— 可复用组合式函数
- `stores/` —— Pinia 状态管理
- `router/` —— Vue Router 配置
- `layout/` —— 外壳/布局组件
- `types/` —— TypeScript 类型定义

## SSE 流式

Chat 流式（`POST /api/chat/send`）用原生 `fetch()` 实现，而非 Axios——Axios 不支持 SSE。所有 SSE 消费方共用同一个工具 `api/sse.ts:streamSSE`，它统一处理传输层关注点：鉴权头、UTF-8 增量解码、`\n` 行缓冲、`data: <json>` 解析、`data: [DONE]` 哨兵，以及 `AbortSignal` 取消。调用方只需提供 URL/body、一个 `extract` 映射器（载荷 → 展示文本）以及 `onChunk/onDone/onError`。`api/chat.ts:sendMessageStream` 是其薄封装；新的流式端点（如 RAG）应复用 `streamSSE` 并自定义 `extract`，而不要重新实现读取循环。Go handler 将 FastAPI 的 SSE 透传到浏览器。

## 嵌入管道

文档上传后，Go 后端向 FastAPI `POST /embedding/build` 发起异步 HTTP 调用（goroutine），流程：
1. 插入一条 `kb_chunk_task` 记录，`task_status=RUNNING`
2. 置 `kb_document.status=PROCESSING`
3. 调用 FastAPI `/embedding/build`
4. 成功：两者分别置 SUCCESS/DONE；失败：置 FAILED 并写 `error_msg`

`kb_document.status` 状态流转：`PENDING` → `PROCESSING` → `DONE` / `FAILED`

## 资源归属

用户私有资源按当前用户隔离——每个 list/get/update/delete 路径都必须对调用者的 user id 校验归属。各模块归属列名不一致：

- KB/文档/Agent/Workflow/Prompt/Tool/MCP：`create_by`
- Chat 会话/消息：`user_id`
- 文件：`upload_by`

归属过滤的强制入口是 `internal/service/owned.go` 的泛型 helper：单条记录校验用 `getOwnedResource[T]`（404/403 三态语义），列表/统计过滤用 `db.Scopes(ownedScope[T](userID))`。列名由模型实现 `model.Owned` 接口声明（`internal/model/owned.go`），新模块嵌入 `UserOwnedModel` 即自动获得 `create_by` 归属。**禁止在 service 层手写 `Where("create_by = ?")` 等字面量归属条件**——此前曾因 list/presign 路径未按 `upload_by` 隔离而出现 IDOR 漏洞。

## RBAC / 鉴权

JWT Claims 包含 `userID`、`username`、`roles`（`[]string`，角色码已带 `ROLE_` 前缀）。中间件解析后注入 `gin.Context`，handler 通过 `c.GetInt64(middleware.CtxUserID)` 取当前用户，通过 `c.GetStringSlice(middleware.CtxRoles)` 取角色。`middleware.AdminRequired()` 中间件检查 roles 是否含 `ROLE_ADMIN`。前端从 `/auth/info` 的 roles 暴露 `isAdmin`，对非管理员隐藏系统菜单，并在路由守卫里加载用户信息。

## 关键 API 约定

所有端点以 `/api/` 为前缀。标准响应包装（`code`、`message`、`data`）来自 `internal/common/result.go`。

Auth：`POST /api/auth/login`、`POST /api/auth/logout`、`GET /api/auth/info`（返回 roles）  
用户管理：`/api/user/`（page/add/update/delete/status）——仅管理员。密码经 bcrypt；更新时用户名不可变；列表响应中剥离哈希。  
Chat：`POST /api/chat/send`（SSE），会话 CRUD 位于 `/api/chat/session/`  
KB/RAG：`/api/kb/`、`/api/document/`、`/api/rag/chat`（SSE）、`/api/rag/rebuild`。前端 RAG 问答页（`views/knowledge/rag`）通过 `api/kb.ts:ragChatStream` 流式接收答案，将 `{type:'sources'}` 元数据帧（可折叠来源引用）与 `{content}` token 帧拆开。  
Files：`/api/file/`（基于 MinIO）。上传加固（P2-6）：服务端校验大小上限与扩展名白名单（`config.yaml` 的 `upload.*`，文件中心用 `file_exts`、知识库文档用 `doc_exts`），存储用 `http.DetectContentType` 嗅探的真实 MIME 而非客户端传入的 Content-Type；presign 过期 1h。校验逻辑在 `internal/common/upload.go`。  
Monitor：`GET /api/dashboard/stats`（按用户计数）；`GET /api/monitor/server` + `GET /api/monitor/health`——仅管理员；服务器指标取自 `gopsutil`，健康检查探测 Redis/FastAPI/MinIO。  
Agent：`/api/agent/`（list/get/add/update/delete + `POST /api/agent/{id}/run`）—— 用户私有（`create_by`）  
Workflow：`/api/workflow/`（list/get/add/update/delete + `POST /api/workflow/{id}/run`）—— 用户私有（`create_by`）。`definition` 为画布序列化的图 JSON（`{nodes:[{id,type,data,position}], edges:[{source,target}]}`，节点类型 start/llm/http/end），由前端 Vue Flow 画布（`components/workflow/WorkflowCanvas.vue`）编辑、FastAPI `app/workflow/engine.py` 拓扑执行（节点输出以 id 存入变量表供下游 `{{nodeId}}` 模板引用）；为空时回退默认单节点 LLM。  
Job：`/api/job/`（page/handlers/add/update/delete/status + `POST /api/job/run/{id}`、`GET /api/job/log/page`、`DELETE /api/job/log/clean`）——仅管理员；cron 通过 `robfig/cron/v3` 调度  
Prompt：`/api/prompt/`（list/get/add/update/delete）—— 用户私有（`create_by`）  
Tool：`/api/tool/`（list/get/add/update/delete）—— 用户私有（`create_by`）；`tool_type` 为 http/builtin，`config` 为 JSON 字符串  
MCP：`/api/mcp/`（list/get/add/update/delete + `POST /api/mcp/test/{id}`）—— 用户私有（`create_by`）  
FastAPI 内部接口（由 Go 后端调用，不对客户端暴露）：`POST /chat`、`POST /rag/chat`、`POST /embedding/build`、`POST /agent/run`、`POST /workflow/run`。所有调用经由 `pkg/fastapi/` 统一客户端，base URL 与超时来自 `config.yaml` 的 `fastapi.*`。  

FastAPI 健康检查：`GET http://localhost:8001/health`

## 数据库关键表

- `sys_user`、`sys_role`、`sys_user_role` —— RBAC 系统（`role_code` 带 `ROLE_` 前缀）
- `chat_session`、`chat_message`、`chat_model` —— chat 模块。`chat_session.summary` + `summary_upto_id` 为会话滚动摘要（P3-2 Memory 层）：消息数超 `summarizeThreshold`(40) 时，Go 侧异步调 FastAPI `/chat/summarize` 把「最近 20 条之前、未摘要」的旧消息压缩进 `summary` 并推进 `summary_upto_id`；上下文组装为「system prompt + summary（作 system 消息）+ id>summary_upto_id 的最近消息」。`chat_model` 由 Go 后端完全管理；FastAPI **不**读 MySQL。多模型路由：Go 按会话/请求的模型名查 `chat_model`，将 `api_url`/`api_key` 以 `llm_config {api_base, api_key}` 字段随 `/chat`、`/rag/chat` 请求透传，FastAPI 据此按请求构建（LRU 缓存）LLM 客户端；未配置时回退 `.env` 的 `LLM_*`。`llm_config` 仅在服务间内网流转，不对客户端暴露。
- `kb_knowledge_base`、`kb_document`、`kb_chunk_task` —— 知识库 + RAG 管道（`kb_chunk_task.task_status`：PENDING/RUNNING/SUCCESS/FAILED；`kb_document.status`：PENDING/PROCESSING/DONE/FAILED）
- `file_info` —— 文件中心（归属列 `upload_by`）
- `agent` —— Agent 定义，`tools` 是 JSON 数组字符串
- `workflow` —— 工作流定义，`definition` 是 JSON 字符串
- `sys_job`、`sys_job_log` —— cron 任务 + 执行日志。`sys_job.status`：0=运行，1=暂停；`invoke_target` 引用 JobHandler 名。`sys_job_log` **没有** `deleted` 列（清理为物理删除），且只有 `create_time`。
- `prompt` —— 提示词库
- `tool` —— 工具注册表
- `mcp_server` —— MCP 服务器注册表；`transport` 为 sse/stdio

所有表均使用 `BIGINT AUTO_INCREMENT` 主键、`deleted BIGINT` 软删除（GORM soft_delete 插件 **milli 模式**：0=未删除，非 0=删除时刻毫秒时间戳）、`utf8mb4` 排序规则。`sys_user`/`sys_role` 的唯一键为 `(username, deleted)` / `(role_code, deleted)` 复合键，软删后可重建同名记录。时间戳由 GORM 的 `AutoCreateTime`/`AutoUpdateTime` 自动填充（`sys_job_log` 例外——只有 `create_time`）。

## Sprint 路线图

1. Sprint 1 ✓ —— Spring Boot 初始化、JWT 认证、RBAC（Spring Boot 已废弃，见 Sprint 10）
2. Sprint 2 ✓ —— 带 SSE 流式与 Markdown 渲染的 Chat
3. Sprint 3 ✓ —— 文件中心（MinIO）+ 知识库 CRUD + 文档管理
4. Sprint 4 ✓ —— RAG 管道：异步嵌入管道；到 FastAPI 的 SSE 代理
5. Sprint 5 ✓ —— 仪表盘统计
6. Sprint 6 ✓ —— 系统监控；Agent + Workflow 模块（CRUD + 运行代理到 FastAPI）；动态 cron 调度器
7. Sprint 7 ✓ —— 提示词中心、工具中心、MCP 服务器注册表；MCP 连通性检测
8. Sprint 8 ✓ —— Agent 运行时工具使用：真实 HTTP 工具执行 + SSE MCP 工具加载，贯通 Go → FastAPI 工具调用循环；agent UI 选择工具/MCP 服务器并展示执行轨迹
9. Sprint 9 ✓ —— 架构优化：统一 FastApiClient、SSE/嵌入线程池隔离、聊天上下文有界化与断连保存、CORS/LLM 超时重试修复；前端统一 streamSSE 流式工具、新增 RAG 流式问答页、Element Plus 按需引入 + 路由级拆包（Element Plus 已废弃，UI 栈已迁移至 TailwindCSS 4 + Radix Vue）
10. Sprint 10 ✓ —— 后端迁移至 Go（Gin + GORM），替换 Spring Boot 多模块架构