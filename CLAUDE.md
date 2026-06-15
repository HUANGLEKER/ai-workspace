# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览

**AI Workspace v1.0** —— 一个集成了 Chat、知识库、RAG、提示词中心、工作流、Agent、MCP、工具中心、文件中心与仪表盘的个人 AI 平台。目标是作为个人 AI 中枢，替代 ChatGPT + Dify + OpenWebUI + Notion AI 的组合。

## 子项目文档（先看对应子目录的 CLAUDE.md）

本仓库为三服务单体仓库，**改某一层前请先读该层的 `CLAUDE.md`**，本根文件只保留跨服务的架构、契约与约定：

| 子项目 | 目录 | 文档 | 职责 |
|---|---|---|---|
| 前端 | `ai-workspace-web/` | [CLAUDE.md](ai-workspace-web/CLAUDE.md) | Vue3 + TS + Tailwind 4 + Radix Vue |
| Go 后端 | `ai-workspace-backend/` | [CLAUDE.md](ai-workspace-backend/CLAUDE.md) | 鉴权 / RBAC / 业务逻辑 / 资源归属 |
| AI 服务 | `ai-service/` | [CLAUDE.md](ai-service/CLAUDE.md) | 所有 LLM 交互（流式/嵌入/RAG/agent/工作流） |

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

三层架构：Vue3 前端 → Go/Gin 后端（REST API）→ FastAPI AI 服务。Go 层负责鉴权 / RBAC / 业务逻辑；FastAPI 负责所有 LLM 交互。**后端不读 LLM，FastAPI 不读 MySQL**，二者经 `pkg/fastapi/` 统一客户端 HTTP 通信。

```
Vue3 (3000) → Go/Gin (8080) → FastAPI (8001) → [Redis, ChromaDB, MinIO] → LLM
```

## 快速启动

> 各服务的完整命令、配置项、规范见对应子目录 `CLAUDE.md`。下表为最小启动序列。

```bash
# 1. 基础设施（MySQL/Redis/MinIO/ChromaDB 全 Docker 化）
docker-compose -f docker-compose.infra.yml up -d

# 2. 前端（ai-workspace-web/，pnpm）
pnpm install && pnpm dev            # 3000，代理 /api → :8080

# 3. Go 后端（ai-workspace-backend/）
make run                            # 8080，config.yaml

# 4. AI 服务（ai-service/，uv）
cp .env.example .env && uv sync && uv run python main.py   # 8001
```

默认凭证：MySQL `root/123456`，管理员 `admin/123456`（首登强制改密）。FastAPI 用 Redis DB 1，Go 用 DB 0。**注意**：应用容器与本机开发进程端口冲突（3000/8080/8001），二者择一。

### 容器化整栈（可选）

```bash
docker-compose -f docker-compose.infra.yml up -d
docker-compose -f docker-compose.app.yml up -d --build      # 入口 http://localhost:3000
```

两个 compose 共享网络 `ai-workspace-net`；后端用 `config.docker.yaml`，AI 服务配置经环境变量注入；前端 nginx 反代 `/api`（已配 SSE 透传 `proxy_buffering off`）；后端公开 `GET /health` 供健康检查。

### 本地基础设施（`docker-compose.infra.yml`）

MySQL 8（3306，`mysql-data`）、Redis 7（6379，AOF）、MinIO（9000 API / 9011 控制台）、ChromaDB（8000），各自持久化卷。

### 数据库

`docker-compose.infra.yml` 把 `ai-workspace/sql/init.sql` 挂为 MySQL 初始化脚本（仅数据卷首次执行），含全部 21 张表 DDL 与种子数据。**维护约定：任何表结构变更必须同步更新 init.sql**（`docker exec ai-workspace-mysql mysqldump -uroot -p123456 --no-data ai_workspace` 重导出）。

## 测试

- 前端：vitest（`pnpm test`），覆盖 utils/api/composables。
- Go 后端：`go test ./...`，service 层已有覆盖。
- AI 服务：无自动化测试。
- CI（`.github/workflows/ci.yml`）：Go gofmt/vet/test/build、前端 vue-tsc/vitest/build、AI 服务 uv 冻结安装 + 导入冒烟。

## 跨服务契约（关键，改任一端都要核对）

- **SSE 流式**：Chat/RAG 用原生 `fetch()`（Axios 不支持 SSE）。前端共用 `api/sse.ts:streamSSE`；FastAPI 产出 token 帧 + 旁路帧 `{type:'sources'|'usage'|'title'|'status'}`；Go handler 透传到浏览器。RAG 须先发 `sources` 帧再逐 token，流尾补 `{type:'usage'}`。
- **多模型路由**：Go 按会话/请求模型名查 `chat_model`，把 `api_url`/`api_key` 以 `llm_config {api_base, api_key}` 随 `/chat`、`/rag/chat` 透传，FastAPI 按请求构建（LRU 缓存）客户端；未配置回退 `.env` 的 `LLM_*`。`llm_config` 仅内网流转。
- **嵌入管道**：Go 异步调 FastAPI `POST /embedding/build`；状态流转 `kb_document.status`：PENDING→PROCESSING→DONE/FAILED；`kb_chunk_task.task_status`：PENDING/RUNNING/SUCCESS/FAILED。文档类型白名单须三处对齐：后端 `config.yaml` 的 `doc_exts`（pdf/txt/md/doc/docx/xls/xlsx）、FastAPI 嵌入 loader、前端 `accept`。
- **链路追踪**：OpenTelemetry 串联 Gin→FastAPI，W3C `traceparent` 传播；两端经端点开关（Go `config.tracing.endpoint`、FastAPI `TRACING_ENDPOINT`），留空 no-op。与既有 `X-Request-ID` 并存。
- **FastAPI 内部接口**（不对客户端暴露）：`POST /chat`、`/rag/chat`、`/embedding/build`、`/agent/run`、`/workflow/run`，`DELETE /embedding/delete`。健康检查 `GET :8001/health`。

## 资源归属（防 IDOR，Go 后端强约束）

用户私有资源按当前用户隔离。归属列名不一致：KB/文档/Agent/Workflow/Prompt/Tool/MCP→`create_by`；Chat/RAG 会话/消息→`user_id`；文件→`upload_by`。**唯一强制入口**是 `internal/service/owned.go` 的泛型 helper（`getOwnedResource[T]` 单条 404/403 三态、`ownedScope[T]` 列表过滤）；**禁止手写 `Where("create_by = ?")` 字面量**。详见后端 CLAUDE.md。

## RBAC / 鉴权

JWT Claims：`userID`、`username`、`roles`（带 `ROLE_` 前缀）。中间件注入 `gin.Context`，`AdminRequired()` 校验 `ROLE_ADMIN`。前端从 `/auth/info` 的 roles 暴露 `isAdmin`，隐藏系统菜单、路由守卫加载用户信息。`chat_model.api_key` AES-256-GCM 加密落库；CORS 白名单、限流分级、强制改密详见后端 CLAUDE.md。

## 关键 API 约定

所有端点以 `/api/` 为前缀，标准响应包装（`code`/`message`/`data`）来自 `internal/common/result.go`。

- **Auth**：`POST /api/auth/login`、`/logout`、`GET /api/auth/info`
- **用户管理**：`/api/user/`（page/add/update/delete/status）—— 仅管理员；bcrypt，更新时用户名不可变
- **Chat**：`POST /api/chat/send`（SSE），会话 CRUD `/api/chat/session/`
- **KB/RAG**：`/api/kb/`、`/api/document/`、`/api/rag/chat`（SSE）、`/api/rag/rebuild`。问答会话独立持久化（`rag_session`/`rag_message`，归属 `user_id`，每会话绑一个 `kb_id`），路由 `/api/rag/session/`（list?kbId=/add/`:id` 改名/`:id/prompt`/`:id` 删/`:id/messages` 历史与清空）。`RAGChat` 以 `sessionId` 为键，持久化问题+断连保存回复（含 `sources` JSON）、首问自动起名（`{type:'title'}` 帧）、透传会话 `system_prompt`。
- **Files**：`/api/file/`（MinIO）。服务端校验大小上限与扩展名白名单（`upload.file_exts`/`doc_exts`），存储用 `http.DetectContentType` 嗅探真实 MIME；presign 过期 1h；校验逻辑在 `internal/common/upload.go`。
- **Monitor**：`GET /api/dashboard/stats`（按用户计数）；`/api/monitor/server`、`/health` —— 仅管理员，指标取自 `gopsutil`，探测 Redis/FastAPI/MinIO。
- **Agent**：`/api/agent/`（CRUD + `POST /{id}/run`）—— 私有 `create_by`
- **Workflow**：`/api/workflow/`（CRUD + `POST /{id}/run`）—— 私有。`definition` 为画布图 JSON（节点 start/llm/http/search/end），Vue Flow 画布编辑、FastAPI `engine.py` 拓扑执行
- **Job**：`/api/job/`（page/handlers/add/update/delete/status + `run/{id}`、`log/page`、`log/clean`）—— 仅管理员；`robfig/cron/v3`
- **Prompt**：`/api/prompt/`（CRUD）—— 私有。与 chat/RAG 打通：输入框 `/` 唤起 `PromptPicker`，可插入或「设为会话系统提示词」（写 `chat_session`/`rag_session` 的 `system_prompt`）
- **Tool**：`/api/tool/`（CRUD）—— 私有；`tool_type` http/builtin，`config` 为 JSON
- **MCP**：`/api/mcp/`（CRUD + `POST /test/{id}`）—— 私有；`transport` sse/stdio

## 数据库关键表

- `sys_user`/`sys_role`/`sys_user_role` —— RBAC（`role_code` 带 `ROLE_` 前缀）
- `chat_session`/`chat_message`/`chat_model` —— chat。`chat_session.summary`+`summary_upto_id` 为滚动摘要（超 `summarizeThreshold`(40) 时 Go 异步调 FastAPI `/chat/summarize` 压缩旧消息）；上下文 = system prompt + summary + id>summary_upto_id 的最近消息。`chat_model` 由 Go 完全管理，FastAPI 不读 MySQL。
- `kb_knowledge_base`/`kb_document`/`kb_chunk_task` —— 知识库 + RAG 管道
- `rag_session`/`rag_message` —— 知识库问答（与 chat 同构独立成表，归属 `user_id`、绑 `kb_id`、`system_prompt`、`sources` JSON）；多轮取最近 N 条历史，不做滚动摘要
- `file_info` —— 文件中心（`upload_by`）
- `agent`（`tools` JSON 数组）、`workflow`（`definition` JSON）
- `sys_job`/`sys_job_log` —— cron + 日志（`sys_job.status` 0=运行/1=暂停；`sys_job_log` 无 `deleted`、物理删除、仅 `create_time`）
- `prompt`、`tool`、`mcp_server`（`transport` sse/stdio）

所有表用 `BIGINT AUTO_INCREMENT` 主键、`deleted BIGINT` 软删除（GORM soft_delete **milli 模式**：0=未删，非 0=删除毫秒时间戳）、`utf8mb4`。`sys_user`/`sys_role` 唯一键为复合 `(username, deleted)`/`(role_code, deleted)`，软删后可重建同名。时间戳由 GORM 自动填充（`sys_job_log` 例外）。

## Sprint 路线图

1. Sprint 1 ✓ —— JWT 认证、RBAC（Spring Boot 已废弃，见 S10）
2. Sprint 2 ✓ —— SSE 流式 + Markdown 的 Chat
3. Sprint 3 ✓ —— 文件中心（MinIO）+ 知识库 CRUD + 文档管理
4. Sprint 4 ✓ —— RAG 管道：异步嵌入；SSE 代理
5. Sprint 5 ✓ —— 仪表盘统计
6. Sprint 6 ✓ —— 系统监控；Agent + Workflow；动态 cron 调度
7. Sprint 7 ✓ —— 提示词中心、工具中心、MCP 注册表；MCP 连通性检测
8. Sprint 8 ✓ —— Agent 运行时工具使用：真实 HTTP 工具 + SSE MCP 工具加载
9. Sprint 9 ✓ —— 架构优化：统一 FastApiClient、线程池隔离、上下文有界化、前端 streamSSE（Element Plus 已废弃，UI 栈迁移至 Tailwind 4 + Radix Vue）
10. Sprint 10 ✓ —— 后端迁移至 Go（Gin + GORM），替换 Spring Boot

# Superpowers-ZH 中文增强版

本项目已安装 superpowers-zh 技能框架（20 个 skills）。

## 核心规则

1. **收到任务时，先检查是否有匹配的 skill** —— 哪怕只有 1% 的可能性也要检查
2. **设计先于编码** —— 收到功能需求时，先用 brainstorming skill 做需求分析
3. **测试先于实现** —— 写代码前先写测试（TDD）
4. **验证先于完成** —— 声称完成前必须运行验证命令

## 可用 Skills

Skills 位于 `.claude/skills/` 目录，每个 skill 有独立的 `SKILL.md` 文件。

- **brainstorming**: 在任何创造性工作之前必须使用此技能——创建功能、构建组件、添加功能或修改行为。在实现之前先探索用户意图、需求和设计。
- **chinese-code-review**: 中文 review 沟通参考——话术模板、分级标注、国内团队常见反模式应对。仅在用户显式 /chinese-code-review 时调用。
- **chinese-commit-conventions**: 中文 commit 与 changelog 配置参考。仅在用户显式 /chinese-commit-conventions 时调用。
- **chinese-documentation**: 中文文档排版参考——中英文空格、全半角标点、术语保留。仅在用户显式 /chinese-documentation 时调用。
- **chinese-git-workflow**: 国内 Git 平台配置参考——Gitee、Coding.net、极狐 GitLab、CNB。仅在用户显式 /chinese-git-workflow 时调用。
- **dispatching-parallel-agents**: 当面对 2 个以上可以独立进行、无共享状态或顺序依赖的任务时使用
- **executing-plans**: 当你有一份书面实现计划需要在单独的会话中执行，并设有审查检查点时使用
- **finishing-a-development-branch**: 当实现完成、所有测试通过、需要决定如何集成工作时使用
- **mcp-builder**: MCP 服务器构建方法论 —— 系统化构建生产级 MCP 工具
- **receiving-code-review**: 收到代码审查反馈后、实施建议之前使用
- **requesting-code-review**: 完成任务、实现重要功能或合并前使用，用于验证工作成果是否符合要求
- **subagent-driven-development**: 当在当前会话中执行包含独立任务的实现计划时使用
- **systematic-debugging**: 遇到任何 bug、测试失败或异常行为时使用，在提出修复方案之前执行
- **test-driven-development**: 在实现任何功能或修复 bug 时使用，在编写实现代码之前
- **using-git-worktrees**: 当需要开始与当前工作区隔离的功能开发，或在执行实现计划之前使用
- **using-superpowers**: 在开始任何对话时使用——确立如何查找和使用技能
- **verification-before-completion**: 在宣称工作完成、已修复或测试通过之前使用，必须运行验证命令并确认输出
- **workflow-runner**: 在 Claude Code / OpenClaw / Cursor 中直接运行 agency-orchestrator YAML 工作流
- **writing-plans**: 当你有规格说明或需求用于多步骤任务时使用，在动手写代码之前
- **writing-skills**: 当创建新技能、编辑现有技能或在部署前验证技能是否有效时使用

## 如何使用

当任务匹配某个 skill 时，使用 `Skill` 工具加载对应 skill 并严格遵循其流程。绝不要用 Read 工具读取 SKILL.md 文件。

如果你认为哪怕只有 1% 的可能性某个 skill 适用于你正在做的事情，你必须调用该 skill 检查。
