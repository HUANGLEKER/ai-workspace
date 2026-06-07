# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本仓库中工作时提供指引。

## 项目概览

**AI Workspace v1.0** —— 一个集成了 Chat、知识库、RAG、提示词中心、工作流、Agent、MCP、工具中心、文件中心与仪表盘的个人 AI 平台。目标是作为个人 AI 中枢，替代 ChatGPT + Dify + OpenWebUI + Notion AI 的组合。

## 技术栈

| 层级 | 技术 |
|---|---|
| 前端 | Vue3 + TypeScript + Element Plus（`ai-workspace-web/`） |
| 后端 | Spring Boot 3.5.0 + JDK 25、MyBatis Plus、JJWT |
| AI 服务 | FastAPI 0.115 + LangGraph + LangChain + OpenAI |
| 数据库 | MySQL 8 |
| 缓存 | Redis 7（localhost:6379） |
| 向量库 | ChromaDB（localhost:8000） |
| 对象存储 | MinIO（localhost:9000） |

## 架构

三层架构：Vue3 前端 → Spring Boot 后端（REST API）→ FastAPI AI 服务。Spring Boot 层负责鉴权 / RBAC / 业务逻辑；FastAPI 负责所有 LLM 交互（流式、嵌入、RAG、agent、工作流）。后端通过 HTTP 与 FastAPI 通信。

```
Vue3 (3000) → Spring Boot (8080) → FastAPI (8001) → [Redis, ChromaDB, MinIO] → LLM
```

## 启动服务

### 前端

```bash
# 在 ai-workspace-web/ 下
npm install
npm run dev      # 开发服务器运行于 3000 端口，代理到 localhost:8080
npm run build    # 生产构建（先 vue-tsc 再 vite build）
```

Element Plus 采用**按需自动引入**（不再全局 `app.use(ElementPlus)`）：`vite.config.ts` 配置了 `unplugin-auto-import`（用于 `ElMessage`/`ElMessageBox` 等）与 `unplugin-vue-components`（搭配 `ElementPlusResolver`，并额外加了一个把模板中直接使用的图标 `<Search/>` 映射到 `@element-plus/icons-vue` 的小型解析器）。**不要**再写 `import { ElMessage } from 'element-plus'` 或全局注册图标——直接使用即可，插件会自动注入导入及其样式。`:icon="X"` 这类脚本绑定仍需显式 `import X`。语言环境（locale）放在 `App.vue` 的 `<el-config-provider>` 中。生成的 `src/auto-imports.d.ts` 与 `src/components.d.ts` **需提交**（并非可忽略的生成产物），因为 `npm run build` 会在 Vite 重新生成它们之前先运行 `vue-tsc`——删除会导致干净环境下的类型检查失败。`build.rollupOptions.output.manualChunks` 仅强制拆分框架核心（`vue-vendor`）与较重的 markdown 栈（`markdown`，随 chat/RAG 懒加载）；Element Plus 交由按路由自然拆分，因此首屏只加载当前页面用到的组件。

### Spring Boot 后端

```bash
# 在 ai-workspace/ 下
./mvnw.cmd clean install -DskipTests          # 构建并安装所有模块到本地仓库
./mvnw.cmd -pl workspace-admin spring-boot:run  # 运行（默认激活 dev profile）
```

`spring-boot:run` 必须指向 `workspace-admin` 模块（`-pl workspace-admin`）；根 pom 是 `pom` 打包的聚合器、无主类，在根目录运行会报 "Unable to find a suitable main class"。需先执行 `install`，让兄弟模块对 admin 模块可用。

配置：`workspace-admin/src/main/resources/application.yml`（基础）与 `application-dev.yml`（dev —— MySQL、Redis）。  
默认凭证：MySQL `root / 123456`，管理员账号 `admin / admin123`。

### FastAPI AI 服务

```bash
# 在 ai-service/ 下
cp .env.example .env          # 填入 LLM key 与服务配置
pip install -r requirements.txt
python main.py                # 以 uvicorn 运行于 8001 端口，开启自动重载
```

关键 `.env` 变量（大写，与 Pydantic 字段名对应）：

- **Chat LLM**：`LLM_API_KEY`、`LLM_API_BASE`、`LLM_MODEL`、`LLM_TIMEOUT`、`LLM_MAX_RETRIES`
- **Embedding（独立）**：`EMBEDDING_API_KEY`、`EMBEDDING_API_BASE`、`EMBEDDING_MODEL`。DeepSeek 不提供嵌入端点，需单独配置其他 OpenAI 兼容提供商（推荐硅基流动 `BAAI/bge-m3`）。`EMBEDDING_API_KEY` / `EMBEDDING_API_BASE` 为空时自动回退使用 `LLM_*` 配置。
- **基础设施**：`REDIS_HOST`、`CHROMA_HOST`、`CHROMA_COLLECTION_PREFIX`、`MINIO_ENDPOINT`、`MINIO_ACCESS_KEY`、`MINIO_SECRET_KEY`、`MINIO_BUCKET`

FastAPI 使用 Redis DB 1；Spring Boot 使用 DB 0。切换嵌入模型后必须重建知识库索引（旧向量与新模型不兼容）。

嵌入管道会把下载的文档写入硬编码的 `/tmp/` 路径（`ai-service/app/embedding/service.py` 的 `_load_document`），这仅适用于 POSIX。RAG/嵌入相关工作请在 WSL/Linux/Docker 下运行 AI 服务——Windows 原生 `python main.py` 会在这些路径上失败。

### 数据库

```bash
# 执行一次以创建表结构并初始化默认管理员
mysql -u root -p < ai-workspace/sql/init.sql
```

### 本地基础设施（全部 Docker 化）

所有基础设施（MySQL、Redis、MinIO、ChromaDB）均通过 `docker-compose.infra.yml` 统一管理：

```bash
docker-compose -f docker-compose.infra.yml up -d
```

- MySQL 8.0（3306）：首次启动自动执行 `ai-workspace/sql/init.sql` 初始化表结构，数据持久化到 `mysql-data` volume
- Redis 7（6379）：开启 AOF 持久化，数据持久化到 `redis-data` volume
- MinIO（9000 S3 API / 9011 控制台）：数据持久化到 `minio-data` volume
- ChromaDB（8000）：数据持久化到 `chroma-data` volume

Spring Boot 配置（`application-dev.yml`）无需修改，端口映射后连接地址不变（localhost:3306 / localhost:6379）。

## 测试

目前尚无测试套件。任何 Spring Boot 模块都没有 `src/test/` 目录，AI 服务也没有 pytest 配置。

## 模块结构

**Spring Boot（`ai-workspace`）** —— Maven 多模块，所有类位于 `com.aiworkspace` 之下：

- `workspace-admin` —— 入口（`AdminApplication`）；`@SpringBootApplication(scanBasePackages="com.aiworkspace")`、`@MapperScan("com.aiworkspace.**.mapper")`
- `workspace-common` —— `Result<T>`、`ResultCode`、`PageResult`、`BusinessException`、`BaseEntity`、`CommonConstants`
- `workspace-framework` —— JWT（`JwtUtil` HS256、`JwtAuthFilter`、`SecurityConfig`）、`MetaObjectHandlerConfig`（自动填充时间戳）、`GlobalExceptionHandler`、Redis 配置、Swagger 配置、统一的 `FastApiClient`/`FastApiProperties`（`client` 包）、异步线程池 `AsyncConfig`
- `workspace-system` —— RBAC：`AuthController`、`SysUserService`、`UserDetailsServiceImpl`、`LoginUser` 主体
- `workspace-chat` —— 会话与消息管理
- `workspace-kb` —— 知识库与文档管理
- `workspace-file` —— 文件中心（MinIO 集成）
- `workspace-monitor` —— 仪表盘统计（`DashboardController`）+ 系统监控（`MonitorController`：服务器运行时指标与依赖健康检查，仅管理员）
- `workspace-agent` —— 用户私有的 Agent 定义（CRUD）+ `POST /api/agent/{id}/run`。运行时会把 agent 引用的工具中心工具（`tools`）与 MCP 服务器（`mcp_servers`）——本人拥有且已启用——解析为完整规格（HTTP endpoint/config、SSE url），再 POST 给 FastAPI `/agent/run` 执行真实的工具调用循环。依赖 `workspace-tool` 与 `workspace-mcp`。
- `workspace-workflow` —— 用户私有的工作流定义（CRUD）+ `POST /api/workflow/{id}/run`，代理到 FastAPI `/workflow/run`
- `workspace-job` —— 仅管理员的动态 cron 调度器：`SysJob`/`SysJobLog`，在 `ThreadPoolTaskScheduler` 上运行时（取消）调度（`JobSchedulerManager`），可插拔的 `JobHandler` bean 通过 `JobHandlerRegistry` 按名解析（内置 `SampleJobHandler`）；启动时重新装载运行中的任务
- `workspace-prompt` —— 用户私有的提示词库（CRUD，`/api/prompt`），按 `createBy` 隔离，支持按标题/分类检索
- `workspace-tool` —— 用户私有的工具注册表（CRUD，`/api/tool`）：name/type(http|builtin)/endpoint/config。HTTP 工具由 agent 运行时执行（见 `workspace-agent`）；`config` JSON 存 `{method, params:[{name,type,description,required}], headers}`
- `workspace-mcp` —— 用户私有的 MCP 服务器注册表（CRUD，`/api/mcp`），含针对 `sse` 传输的 `POST /api/mcp/test/{id}` HTTP 可达性检查；`sse` 服务器的工具由 agent 运行时加载

**FastAPI（`ai-service/app`）：**

- `chat/` —— LLM 调用、SSE 流式（`POST /chat`）
- `rag/` —— 向量检索 + 答案生成（`POST /rag/chat`）
- `embedding/` —— 文档切片、嵌入、写入 ChromaDB（`POST /embedding/build`、`DELETE /embedding/delete`）
- `agent/` —— 工具调用 agent（`POST /agent/run`）：绑定由 `HttpToolSpec` 构建的 HTTP 工具（经 `httpx` 执行），外加通过 `langchain-mcp-adapters`（`MultiServerMCPClient`）从 SSE MCP 服务器加载的工具，然后运行有界的 think→act 循环，返回答案与 `steps` 轨迹。MCP 导入做了保护，缺少该可选库时服务仍可运行。
- `workflow/` —— 基于 LangGraph 的引擎（`POST /workflow/run`）
- `llm/provider.py` —— LLM 提供方抽象（通过 LangChain 接 OpenAI）
- `vectorstore/chroma_client.py` —— ChromaDB HTTP 客户端
- `config/settings.py` —— 从 `.env` 加载的 Pydantic `BaseSettings`
- `models/` —— 各功能的 Pydantic 请求/响应模型
- `utils/response.py` —— 标准化 JSON 响应包装

**前端（`ai-workspace-web/src`）：**

- `api/` —— 各功能的 Axios HTTP 客户端模块
- `views/` —— 页面组件
- `stores/` —— Pinia 状态管理
- `router/` —— Vue Router 配置
- `layout/` —— 外壳/布局组件
- `types/` —— TypeScript 类型定义

## SSE 流式

Chat 流式（`POST /api/chat/send`）用原生 `fetch()` 实现，而非 Axios——Axios 不支持 SSE。所有 SSE 消费方共用同一个工具 `api/sse.ts:streamSSE`，它统一处理传输层关注点：鉴权头、UTF-8 增量解码、`\n` 行缓冲、`data: <json>` 解析、`data: [DONE]` 哨兵，以及 `AbortSignal` 取消。调用方只需提供 URL/body、一个 `extract` 映射器（载荷 → 展示文本）以及 `onChunk/onDone/onError`。`api/chat.ts:sendMessageStream` 是其薄封装；新的流式端点（如 RAG）应复用 `streamSSE` 并自定义 `extract`，而不要重新实现读取循环。Spring Boot 控制器将 FastAPI 的该 SSE 透传到浏览器。

## 异步嵌入管道

文档上传后，`EmbeddingService.buildAsync()`（标注 `@Async("taskExecutor")`）运行在 `workspace-framework/async/AsyncConfig.java` 定义的嵌入线程池上（`taskExecutor`：核心 5 / 最大 10 / 队列 100，`CallerRunsPolicy`）。SSE 代理任务（Chat/RAG 流式）运行在**独立**的 `streamExecutor`（核心 20 / 最大 200 / 队列 0），使长连接流不会饿死嵌入管道。流程：
1. 插入一条 `KbChunkTask` 记录，`task_status=RUNNING`
2. 置 `kb_document.status=PROCESSING`
3. POST 到 FastAPI `POST /embedding/build`（在异步线程上发起阻塞式 HTTP 调用）
4. 成功：两者分别置 SUCCESS/DONE；失败：置 FAILED 并写 `error_msg`

`kb_document.status` 状态流转：`PENDING` → `PROCESSING` → `DONE` / `FAILED`

## 资源归属

用户私有资源（知识库、文档、文件、聊天会话/消息）按当前用户隔离——每个 list/get/update/delete/upload 路径都必须对调用者的 user id 校验归属。新增端点时遵循的约定：

- Service 暴露 `getOwned(id, userId)` 辅助方法，当行不存在或不属于 `userId` 时抛 `BusinessException`。变更类端点在操作前调用它。
- 列表查询按归属列过滤。注意该列因表而异：KB/文档/文件用 `createBy`；chat 用 `userId`。
- 更新时，从已存在的行重新设置归属列，防止通过请求体改写归属。
- 仪表盘计数按当前用户隔离，而非全局。

`@Async` 自调用不经过 Spring 代理，会同步执行。调用异步方法（如 `EmbeddingService` 的 rebuild/build）要通过注入的 bean 引用，而非 `this.`。

## RBAC / 方法级安全

`LoginUser` 的权限从角色码加载（`sys_user_role` → `sys_role.role_code`），其中 `role_code` 已带 `ROLE_` 前缀，故 `@PreAuthorize("hasRole('ADMIN')")` 可直接生效。前端从 `/auth/info` 的 roles 暴露 `isAdmin`，对非管理员隐藏系统菜单，并在路由中做守卫（用户信息在路由守卫里加载，因此刷新/直接导航也能生效）。

## 资源归属补充说明

各模块的归属列名不一致——`file_info` 按 `uploadBy`，KB/文档按 `createBy`，chat 按 `userId`。新增过滤前先确认正确的列；此前曾因 list/presign 路径未按 `uploadBy` 隔离而出现 IDOR 漏洞导致文件泄露。

## 关键 API 约定

所有 Spring Boot REST 端点以 `/api/` 为前缀。标准响应包装（`code`、`message`、`data`）来自 `workspace-common/Result.java`。

Auth：`POST /api/auth/login`、`POST /api/auth/logout`、`GET /api/auth/info`（返回 roles）  
用户管理：`/api/user/`（page/add/update/delete/status），位于 `workspace-system` 的 `SysUserController`——以 `@PreAuthorize("hasRole('ADMIN')")` 锁定。密码经 BCrypt 编码；更新时用户名不可变；仅当传入新密码时才重新哈希；列表响应中剥离哈希。  
Chat：`POST /api/chat/send`（SSE），会话 CRUD 位于 `/api/chat/session/`  
KB/RAG：`/api/kb/`、`/api/document/`、`/api/rag/chat`（SSE）、`/api/rag/rebuild`。前端 RAG 问答页（`views/knowledge/rag`，路由 `/knowledge/rag`）通过 `api/kb.ts:ragChatStream` 流式接收答案，它复用共享的 `streamSSE` 并自定义 `extract`，将 `{type:'sources'}` 元数据帧（渲染为可折叠的来源引用）与 `{content}` token 帧拆开。  
Files：`/api/file/`（基于 MinIO）  
Monitor：`GET /api/dashboard/stats`（按用户计数）；`GET /api/monitor/server` + `GET /api/monitor/health`，位于 `workspace-monitor` 的 `MonitorController`——仅管理员（`@PreAuthorize("hasRole('ADMIN')")`），无 DB 表；服务器指标取自 JDK MXBeans，健康检查探测 Redis（经 `RedisConnectionFactory.ping`）、FastAPI（`/health`）与 MinIO（`/minio/health/live`）。  
Agent：`/api/agent/`（list/get/add/update/delete + `POST /api/agent/{id}/run`）—— 用户私有（`createBy`）
Workflow：`/api/workflow/`（list/get/add/update/delete + `POST /api/workflow/{id}/run`）—— 用户私有（`createBy`）
Job：`/api/job/`（page/handlers/add/update/delete/status + `POST /api/job/run/{id}`、`GET /api/job/log/page`、`DELETE /api/job/log/clean`）—— 仅管理员（`@PreAuthorize("hasRole('ADMIN')")`）；cron 为 Spring 6 段式，经 `CronExpression.isValidExpression` 校验
Prompt：`/api/prompt/`（list/get/add/update/delete）—— 用户私有（`createBy`）
Tool：`/api/tool/`（list/get/add/update/delete）—— 用户私有（`createBy`）
MCP：`/api/mcp/`（list/get/add/update/delete + `POST /api/mcp/test/{id}`）—— 用户私有（`createBy`）
FastAPI 内部接口（由 Spring Boot 调用，不对客户端暴露）：`POST /chat`、`POST /rag/chat`、`POST /embedding/build`、`POST /agent/run`、`POST /workflow/run`。这些调用全部经由单一共享的 `FastApiClient`（`workspace-framework/client`，基于 JDK `HttpClient`，连接池复用 + 集中超时）——`postForData` 用于一元 JSON（agent/workflow），`send` 用于 build/delete（embedding），`stream` 用于 SSE 代理（chat/rag）。Base URL 与超时来自 `FastApiProperties`（`application.yml` 中的 `fastapi.*`）；不要再引入按调用各自创建的 `HttpURLConnection`。

Swagger UI：`http://localhost:8080/swagger-ui.html`  
FastAPI 健康检查：`GET http://localhost:8001/health`

## 数据库关键表

- `sys_user`、`sys_role`、`sys_menu`、`sys_user_role`、`sys_role_menu` —— RBAC 系统
- `chat_session`、`chat_message`、`chat_model` —— chat 模块。`chat_model` 表完全由 Spring Boot 拥有与管理（`workspace-chat` 的 `ChatModelController`/`ChatModelService`，管理 UI 在 `views/system/model`）。FastAPI **不**读 MySQL；选定的模型名按请求在 chat 载荷中透传（`ai-service/app/models/chat.py` 的 `model` 字段），缺省时回退到 AI 服务的 `LLM_MODEL`。
- `kb_knowledge_base`、`kb_document`、`kb_chunk_task` —— 知识库 + RAG 管道（`kb_chunk_task.task_status`：PENDING/RUNNING/SUCCESS/FAILED；`kb_document.status`：PENDING/PROCESSING/DONE/FAILED）
- `file_info` —— 文件中心
- `agent` —— Agent 定义（`workspace-agent`），通过 `createBy` 用户私有；`tools` 是 JSON 数组字符串
- `workflow` —— 工作流定义（`workspace-workflow`），通过 `createBy` 用户私有；`definition` 是 JSON 字符串
- `sys_job`、`sys_job_log` —— cron 任务 + 执行日志（`workspace-job`）。`sys_job.status`：0=运行/已调度，1=暂停；`invoke_target` 引用某个 `JobHandler` bean 名。`sys_job_log` **没有** `deleted` 列（"清理日志"为物理删除），且只有 `create_time`。
- `prompt` —— 提示词库（`workspace-prompt`），通过 `createBy` 用户私有
- `tool` —— 工具注册表（`workspace-tool`），通过 `createBy` 用户私有；`tool_type` 为 http/builtin，`config` 为 JSON 字符串
- `mcp_server` —— MCP 服务器注册表（`workspace-mcp`），通过 `createBy` 用户私有；`transport` 为 sse/stdio

所有表均使用 `BIGINT AUTO_INCREMENT` 主键、通过 `deleted TINYINT` 软删除、`utf8mb4` 排序规则。时间戳由 `MetaObjectHandlerConfig` 自动填充（`sys_job_log` 例外——只追加，仅填 `create_time`）。

## Sprint 路线图

1. Sprint 1 ✓ —— Spring Boot 初始化、JWT 认证、RBAC
2. Sprint 2 ✓ —— 带 SSE 流式与 Markdown 渲染的 Chat
3. Sprint 3 ✓ —— 文件中心（MinIO）+ 知识库 CRUD + 文档管理
4. Sprint 4 ✓ —— RAG 管道：实现 workspace-chat/file/kb 模块；异步嵌入管道；到 FastAPI 的 SSE 代理
5. Sprint 5 ✓ —— 仪表盘统计：`workspace-monitor` 的 `GET /api/dashboard/stats`；前端挂载时拉取
6. Sprint 6 ✓ —— 系统监控（`workspace-monitor` 的 `MonitorController`）；Agent + Workflow 模块（CRUD + 运行代理到 FastAPI）；动态 cron 调度器（`workspace-job`）
7. Sprint 7 ✓ —— 提示词中心（`workspace-prompt`）、工具中心（`workspace-tool`）、MCP 服务器注册表（`workspace-mcp`）—— 用户私有 CRUD；MCP 连通性检测
8. Sprint 8 ✓ —— Agent 运行时工具使用：真实 HTTP 工具执行 + SSE MCP 工具加载，贯通 Spring → FastAPI 工具调用循环；agent UI 选择工具/MCP 服务器并展示执行轨迹
9. Sprint 9 ✓ —— 架构优化：统一 `FastApiClient`（连接池 + 集中超时）、SSE/嵌入线程池隔离、聊天上下文有界化与断连保存、CORS/LLM 超时重试修复；前端统一 `streamSSE` 流式工具、新增 RAG 流式问答页、Element Plus 按需引入 + 路由级拆包
