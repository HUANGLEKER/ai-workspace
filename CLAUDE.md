# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**AI Workspace v1.0** — a personal AI platform integrating Chat, Knowledge Base, RAG, Prompt Center, Workflow, Agent, MCP, Tool Center, File Center, and Dashboard. Intended to replace ChatGPT + Dify + OpenWebUI + Notion AI as a personal AI hub.

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | Vue3 + TypeScript + Element Plus (`ai-workspace-web/`) |
| Backend | Spring Boot 3.5.0 + JDK 25, MyBatis Plus, JJWT |
| AI Service | FastAPI 0.115 + LangGraph + LangChain + OpenAI |
| Database | MySQL 8 |
| Cache | Redis 5 (localhost:6379) |
| Vector DB | ChromaDB (localhost:8000) |
| Storage | MinIO (localhost:9000) |

## Architecture

Three-tier architecture: Vue3 frontend → Spring Boot backend (REST API) → FastAPI AI service. The Spring Boot layer handles auth/RBAC/business logic; FastAPI handles all LLM interactions (streaming, embedding, RAG, agent, workflow). Backend communicates with FastAPI over HTTP.

```
Vue3 (3000) → Spring Boot (8080) → FastAPI (8001) → [Redis, ChromaDB, MinIO] → LLM
```

## Running the Services

### Frontend

```bash
# From ai-workspace-web/
npm install
npm run dev      # dev server on port 3000 with proxy to localhost:8080
npm run build    # production build (runs vue-tsc then vite build)
```

Element Plus is **auto-imported on demand** (no global `app.use(ElementPlus)`): `vite.config.ts` wires `unplugin-auto-import` (for `ElMessage`/`ElMessageBox` etc.) and `unplugin-vue-components` with `ElementPlusResolver` plus a small resolver that maps template-used icons (`<Search/>`) to `@element-plus/icons-vue`. Do **not** add `import { ElMessage } from 'element-plus'` or globally register icons — just use them; the plugins inject the import and its styles. `:icon="X"` script bindings still import `X` explicitly. Locale lives in `App.vue`'s `<el-config-provider>`. The generated `src/auto-imports.d.ts` and `src/components.d.ts` are committed (not generated artifacts to ignore) because `npm run build` runs `vue-tsc` before Vite regenerates them — deleting them breaks a clean type-check. `build.rollupOptions.output.manualChunks` force-chunks only the framework core (`vue-vendor`) and the heavy markdown stack (`markdown`, lazy with chat/RAG); Element Plus is left to per-route splitting so first paint pulls only the components a page uses.

### Spring Boot backend

```bash
# From ai-workspace/
./mvnw.cmd clean install -DskipTests          # build + install all modules to local repo
./mvnw.cmd -pl workspace-admin spring-boot:run  # run (dev profile active by default)
```

`spring-boot:run` must target the `workspace-admin` module (`-pl workspace-admin`); the root pom is a `pom`-packaging aggregator with no main class, so running it there fails with "Unable to find a suitable main class". Run `install` first so the sibling modules are available to the admin module.

Config: `workspace-admin/src/main/resources/application.yml` (base) and `application-dev.yml` (dev — MySQL, Redis).  
Default credentials: MySQL `root / 123456`, admin user `admin / admin123`.

### FastAPI AI service

```bash
# From ai-service/
cp .env.example .env          # fill in LLM key and service config
pip install -r requirements.txt
python main.py                # runs uvicorn on port 8001 with auto-reload
```

Key `.env` variables (uppercase, matching Pydantic field names): `LLM_API_KEY`, `LLM_API_BASE`, `LLM_MODEL`, `LLM_EMBEDDING_MODEL`, `REDIS_HOST`, `CHROMA_HOST`, `CHROMA_COLLECTION_PREFIX`, `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`. FastAPI uses Redis DB 1; Spring Boot uses DB 0.

The embedding pipeline writes downloaded documents to hardcoded `/tmp/` paths (`ai-service/app/embedding/service.py`, `_load_document`), which is POSIX-only. Run the AI service under WSL/Linux/Docker for RAG/embedding work — native Windows `python main.py` will fail on those paths.

### Database

```bash
# Run once to create schema and seed default admin
mysql -u root -p < ai-workspace/sql/init.sql
```

### Local infra (MinIO + ChromaDB)

MySQL and Redis run natively on this machine; MinIO and ChromaDB do not. Without them, Chat and RBAC still work, but file upload/download and RAG vector retrieval do not. The README documents bringing up only the two missing components via `docker-compose.infra.yml` (MinIO on 9000/9001, ChromaDB on 8000) — do not containerize MySQL/Redis as well, to avoid port conflicts.

## Tests

No test suite exists yet. There are no `src/test/` directories in any Spring Boot module and no pytest config in the AI service.

## Module Structure

**Spring Boot (`ai-workspace`)** — Maven multi-module, all classes under `com.aiworkspace`:

- `workspace-admin` — entry point (`AdminApplication`); `@SpringBootApplication(scanBasePackages="com.aiworkspace")`, `@MapperScan("com.aiworkspace.**.mapper")`
- `workspace-common` — `Result<T>`, `ResultCode`, `PageResult`, `BusinessException`, `BaseEntity`, `CommonConstants`
- `workspace-framework` — JWT (`JwtUtil` HS256, `JwtAuthFilter`, `SecurityConfig`), `MetaObjectHandlerConfig` (auto-fill timestamps), `GlobalExceptionHandler`, Redis config, Swagger config
- `workspace-system` — RBAC: `AuthController`, `SysUserService`, `UserDetailsServiceImpl`, `LoginUser` principal
- `workspace-chat` — session and message management
- `workspace-kb` — knowledge base and document management
- `workspace-file` — file center (MinIO integration)
- `workspace-monitor` — dashboard stats (`DashboardController`) + system monitoring (`MonitorController`: server runtime metrics and dependency health checks, admin-only)
- `workspace-agent` — user-owned Agent definitions (CRUD) + `POST /api/agent/{id}/run`. On run it resolves the agent's referenced Tool Center tools (`tools`) and MCP servers (`mcp_servers`) — owned + enabled — into rich specs (HTTP endpoint/config, SSE url) and posts them to FastAPI `/agent/run`, which executes a real tool-calling loop. Depends on `workspace-tool` and `workspace-mcp`.
- `workspace-workflow` — user-owned Workflow definitions (CRUD) + `POST /api/workflow/{id}/run` proxying FastAPI `/workflow/run`
- `workspace-job` — admin-only dynamic cron scheduler: `SysJob`/`SysJobLog`, runtime (un)scheduling on a `ThreadPoolTaskScheduler` (`JobSchedulerManager`), pluggable `JobHandler` beans resolved by name via `JobHandlerRegistry` (`SampleJobHandler` shipped); running jobs re-armed on startup
- `workspace-prompt` — user-owned prompt library (CRUD, `/api/prompt`), `createBy`-scoped with title/category search
- `workspace-tool` — user-owned tool registry (CRUD, `/api/tool`): name/type(http|builtin)/endpoint/config. HTTP tools are executed by the agent at run time (see `workspace-agent`); `config` JSON holds `{method, params:[{name,type,description,required}], headers}`
- `workspace-mcp` — user-owned MCP server registry (CRUD, `/api/mcp`) with `POST /api/mcp/test/{id}` HTTP reachability check for `sse` transport; `sse` servers' tools are loaded by the agent at run time

**FastAPI (`ai-service/app`):**

- `chat/` — LLM invocation, SSE streaming (`POST /chat`)
- `rag/` — vector retrieval + answer generation (`POST /rag/chat`)
- `embedding/` — document chunking, embedding, ChromaDB writes (`POST /embedding/build`, `DELETE /embedding/delete`)
- `agent/` — tool-calling agent (`POST /agent/run`): binds HTTP tools built from `HttpToolSpec` (executed via `httpx`) plus tools loaded from SSE MCP servers via `langchain-mcp-adapters` (`MultiServerMCPClient`), then runs a bounded think→act loop returning the answer and a `steps` trace. MCP import is guarded so the service runs without the optional lib.
- `workflow/` — LangGraph-based engine (`POST /workflow/run`)
- `llm/provider.py` — LLM provider abstraction (OpenAI via LangChain)
- `vectorstore/chroma_client.py` — ChromaDB HTTP client
- `config/settings.py` — Pydantic `BaseSettings` loading from `.env`
- `models/` — Pydantic request/response schemas per feature
- `utils/response.py` — standardized JSON response wrapper

**Frontend (`ai-workspace-web/src`):**

- `api/` — Axios HTTP client modules per feature
- `views/` — page components
- `stores/` — Pinia state management
- `router/` — Vue Router config
- `layout/` — shell/layout components
- `types/` — TypeScript type definitions

## SSE Streaming

Chat streaming (`POST /api/chat/send`) is implemented with raw `fetch()`, not Axios — Axios does not support SSE. All SSE consumers share one helper, `api/sse.ts:streamSSE`, which owns the transport concerns: auth header, incremental UTF-8 decode, `\n` line buffering, `data: <json>` parsing, the `data: [DONE]` sentinel, and `AbortSignal` cancellation. Callers supply only URL/body, an `extract` mapper (payload → display text), and `onChunk/onDone/onError`. `api/chat.ts:sendMessageStream` is a thin wrapper over it; new streaming endpoints (e.g. RAG) should reuse `streamSSE` with a custom `extract` rather than re-implementing the reader loop. The Spring Boot controller proxies this SSE from FastAPI through to the browser.

## Async Embedding Pipeline

When a document is uploaded, `EmbeddingService.buildAsync()` (annotated `@Async("taskExecutor")`) runs on the embedding thread pool defined in `workspace-framework/async/AsyncConfig.java` (`taskExecutor`: 5 core / 10 max / 100 queue, `CallerRunsPolicy`). SSE proxy tasks (Chat/RAG streaming) run on a **separate** `streamExecutor` (20 core / 200 max / 0 queue) so long-lived streams can't starve the embedding pipeline. It:
1. Inserts a `KbChunkTask` record with `task_status=RUNNING`
2. Sets `kb_document.status=PROCESSING`
3. POSTs to FastAPI `POST /embedding/build` (blocking HTTP call on async thread)
4. On success: sets both to SUCCESS/DONE; on failure: FAILED with `error_msg`

`kb_document.status` states: `PENDING` → `PROCESSING` → `DONE` / `FAILED`

## Resource Ownership

User-owned resources (knowledge bases, documents, files, chat sessions/messages) are scoped to the current user — every list/get/update/delete/upload path must verify ownership against the caller's user id. Conventions to follow when adding endpoints:

- Services expose a `getOwned(id, userId)` helper that throws `BusinessException` if the row is missing or not owned by `userId`. Mutating endpoints call it before acting.
- List queries filter by the owner column. Note the column differs by table: KB/document/file use `createBy`; chat uses `userId`.
- On update, re-set the owner column from the existing row to prevent owner reassignment via the request body.
- Dashboard counts are scoped to the current user, not global.

`@Async` self-invocation does not go through the Spring proxy, so it runs synchronously. Call async methods (e.g. `EmbeddingService` rebuild/build) via the injected bean reference, not `this.`.

## RBAC / Method Security

`LoginUser` authorities are loaded from role codes (`sys_user_role` → `sys_role.role_code`), where `role_code` already carries the `ROLE_` prefix, so `@PreAuthorize("hasRole('ADMIN')")` works directly. The frontend exposes `isAdmin` from `/auth/info` roles, hides the system menu for non-admins, and guards the route in the router (user info is loaded in the router guard so it survives refresh/direct navigation).

## Resource Ownership note

Ownership column names are inconsistent across modules — `file_info` scopes by `uploadBy`, KB/document by `createBy`, chat by `userId`. Verify the right column before adding filters; an IDOR bug previously leaked files because the list/presign paths weren't scoped to `uploadBy`.

## Key API Conventions

All Spring Boot REST endpoints are prefixed `/api/`. Standard response wrapper (`code`, `message`, `data`) from `workspace-common/Result.java`.

Auth: `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/info` (returns roles)  
User management: `/api/user/` (page/add/update/delete/status) in `workspace-system` `SysUserController` — locked with `@PreAuthorize("hasRole('ADMIN')")`. Passwords BCrypt-encoded; username immutable on update; password re-hashed only when a new one is sent; hashes stripped from list responses.  
Chat: `POST /api/chat/send` (SSE), session CRUD under `/api/chat/session/`  
KB/RAG: `/api/kb/`, `/api/document/`, `/api/rag/chat` (SSE), `/api/rag/rebuild`. The frontend RAG Q&A page (`views/knowledge/rag`, route `/knowledge/rag`) streams answers via `api/kb.ts:ragChatStream`, which reuses the shared `streamSSE` with a custom `extract` that splits the `{type:'sources'}` metadata frame (rendered as collapsible source citations) from the `{content}` token frames.  
Files: `/api/file/` (MinIO-backed)  
Monitor: `GET /api/dashboard/stats` (per-user counts); `GET /api/monitor/server` + `GET /api/monitor/health` in `workspace-monitor` `MonitorController` — admin-only (`@PreAuthorize("hasRole('ADMIN')")`), no DB tables; server metrics come from JDK MXBeans, health probes Redis (via `RedisConnectionFactory.ping`), FastAPI (`/health`), and MinIO (`/minio/health/live`).  
Agent: `/api/agent/` (list/get/add/update/delete + `POST /api/agent/{id}/run`) — user-owned (`createBy`)
Workflow: `/api/workflow/` (list/get/add/update/delete + `POST /api/workflow/{id}/run`) — user-owned (`createBy`)
Job: `/api/job/` (page/handlers/add/update/delete/status + `POST /api/job/run/{id}`, `GET /api/job/log/page`, `DELETE /api/job/log/clean`) — admin-only (`@PreAuthorize("hasRole('ADMIN')")`); cron is Spring 6-field, validated via `CronExpression.isValidExpression`
Prompt: `/api/prompt/` (list/get/add/update/delete) — user-owned (`createBy`)
Tool: `/api/tool/` (list/get/add/update/delete) — user-owned (`createBy`)
MCP: `/api/mcp/` (list/get/add/update/delete + `POST /api/mcp/test/{id}`) — user-owned (`createBy`)
FastAPI internal (called by Spring Boot, not exposed to clients): `POST /chat`, `POST /rag/chat`, `POST /embedding/build`, `POST /agent/run`, `POST /workflow/run`. All of these go through the single shared `FastApiClient` (`workspace-framework/client`, JDK `HttpClient` with pooled connections + centralized timeouts) — `postForData` for unary JSON (agent/workflow), `send` for build/delete (embedding), `stream` for SSE proxy (chat/rag). Base URL and timeouts come from `FastApiProperties` (`fastapi.*` in `application.yml`); do not re-introduce per-call `HttpURLConnection`.

Swagger UI: `http://localhost:8080/swagger-ui.html`  
FastAPI health check: `GET http://localhost:8001/health`

## Database Key Tables

- `sys_user`, `sys_role`, `sys_menu`, `sys_user_role`, `sys_role_menu` — RBAC system
- `chat_session`, `chat_message`, `chat_model` — chat module. The `chat_model` table is owned and managed entirely by Spring Boot (`workspace-chat` `ChatModelController`/`ChatModelService`, admin UI at `views/system/model`). FastAPI does **not** read MySQL; the selected model name is passed through per request in the chat payload (`model` field in `ai-service/app/models/chat.py`), defaulting to the AI service's `LLM_MODEL` when absent.
- `kb_knowledge_base`, `kb_document`, `kb_chunk_task` — knowledge base + RAG pipeline (`kb_chunk_task.task_status`: PENDING/RUNNING/SUCCESS/FAILED; `kb_document.status`: PENDING/PROCESSING/DONE/FAILED)
- `file_info` — file center
- `agent` — Agent definitions (`workspace-agent`), user-owned via `createBy`; `tools` is a JSON-array string
- `workflow` — Workflow definitions (`workspace-workflow`), user-owned via `createBy`; `definition` is a JSON string
- `sys_job`, `sys_job_log` — cron jobs + execution logs (`workspace-job`). `sys_job.status`: 0=running/scheduled, 1=paused; `invoke_target` references a `JobHandler` bean name. `sys_job_log` has **no** `deleted` column (physical-delete on "clean logs") and only a `create_time`.
- `prompt` — prompt library (`workspace-prompt`), user-owned via `createBy`
- `tool` — tool registry (`workspace-tool`), user-owned via `createBy`; `tool_type` is http/builtin, `config` a JSON string
- `mcp_server` — MCP server registry (`workspace-mcp`), user-owned via `createBy`; `transport` is sse/stdio

All tables use `BIGINT AUTO_INCREMENT` PKs, soft-delete via `deleted TINYINT`, and `utf8mb4` collation. Timestamps auto-filled by `MetaObjectHandlerConfig` (`sys_job_log` is the exception — append-only, only `create_time` filled).

## Sprint Roadmap

1. Sprint 1 ✓ — Spring Boot init, JWT auth, RBAC
2. Sprint 2 ✓ — Chat with SSE streaming and Markdown rendering
3. Sprint 3 ✓ — File center (MinIO) + KB CRUD + document management
4. Sprint 4 ✓ — RAG pipeline: workspace-chat/file/kb modules implemented; async embedding pipeline; SSE proxy to FastAPI
5. Sprint 5 ✓ — Dashboard stats: `GET /api/dashboard/stats` in `workspace-monitor`; frontend fetches on mount
6. Sprint 6 ✓ — System monitoring (`workspace-monitor` `MonitorController`); Agent + Workflow modules (CRUD + run proxy to FastAPI); dynamic cron scheduler (`workspace-job`)
7. Sprint 7 ✓ — Prompt Center (`workspace-prompt`), Tool Center (`workspace-tool`), MCP server registry (`workspace-mcp`) — user-owned CRUD; MCP connectivity test
8. Sprint 8 ✓ — Agent runtime tool use: real HTTP tool execution + SSE MCP tool loading wired through Spring → FastAPI tool-calling loop; agent UI picks tools/MCP servers and shows the execution trace