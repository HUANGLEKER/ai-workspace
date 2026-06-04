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

### Spring Boot backend

```bash
# From ai-workspace/
./mvnw.cmd clean package -DskipTests   # build
./mvnw.cmd spring-boot:run             # run (dev profile active by default)
```

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

### Database

```bash
# Run once to create schema and seed default admin
mysql -u root -p < ai-workspace/sql/init.sql
```

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
- `workspace-agent`, `workspace-workflow`, `workspace-monitor`, `workspace-job` — scaffolded, not yet implemented

**FastAPI (`ai-service/app`):**

- `chat/` — LLM invocation, SSE streaming (`POST /chat`)
- `rag/` — vector retrieval + answer generation (`POST /rag/chat`)
- `embedding/` — document chunking, embedding, ChromaDB writes (`POST /embedding/build`, `DELETE /embedding/delete`)
- `agent/`, `workflow/` — LangGraph-based engines (`POST /agent/run`, `POST /workflow/run`)
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

Chat streaming (`POST /api/chat/send`) is implemented with raw `fetch()`, not Axios — Axios does not support SSE. The frontend (`api/chat.ts:sendMessageStream`) reads the response body as a `ReadableStream`, splits on `\n`, and parses `data: <json>` lines. The stream ends with `data: [DONE]`. The Spring Boot controller proxies this SSE from FastAPI through to the browser.

## Async Embedding Pipeline

When a document is uploaded, `EmbeddingService.buildAsync()` (annotated `@Async("taskExecutor")`) runs on the thread pool defined in `workspace-framework/async/AsyncConfig.java` (10 core / 50 max / 200 queue). It:
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

## Key API Conventions

All Spring Boot REST endpoints are prefixed `/api/`. Standard response wrapper (`code`, `message`, `data`) from `workspace-common/Result.java`.

Auth: `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/info`  
Chat: `POST /api/chat/send` (SSE), session CRUD under `/api/chat/session/`  
KB/RAG: `/api/kb/`, `/api/document/`, `/api/rag/chat`, `/api/rag/rebuild`  
Files: `/api/file/` (MinIO-backed)  
FastAPI internal (called by Spring Boot, not exposed to clients): `POST /chat`, `POST /rag/chat`, `POST /embedding/build`

Swagger UI: `http://localhost:8080/swagger-ui.html`  
FastAPI health check: `GET http://localhost:8001/health`

## Database Key Tables

- `sys_user`, `sys_role`, `sys_menu`, `sys_user_role`, `sys_role_menu` — RBAC system
- `chat_session`, `chat_message`, `chat_model` — chat module
- `kb_knowledge_base`, `kb_document`, `kb_chunk_task` — knowledge base + RAG pipeline (`kb_chunk_task.task_status`: PENDING/RUNNING/SUCCESS/FAILED; `kb_document.status`: PENDING/PROCESSING/DONE/FAILED)
- `file_info` — file center

All tables use `BIGINT AUTO_INCREMENT` PKs, soft-delete via `deleted TINYINT`, and `utf8mb4` collation. Timestamps auto-filled by `MetaObjectHandlerConfig`.

## Sprint Roadmap

1. Sprint 1 ✓ — Spring Boot init, JWT auth, RBAC
2. Sprint 2 ✓ — Chat with SSE streaming and Markdown rendering
3. Sprint 3 ✓ — File center (MinIO) + KB CRUD + document management
4. Sprint 4 ✓ — RAG pipeline: workspace-chat/file/kb modules implemented; async embedding pipeline; SSE proxy to FastAPI
5. Sprint 5 ✓ — Dashboard stats: `GET /api/dashboard/stats` in `workspace-monitor`; frontend fetches on mount