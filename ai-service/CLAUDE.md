# CLAUDE.md —— AI 服务（ai-service）

> 本文件仅覆盖 FastAPI AI 服务子项目。跨服务约定、整体架构见仓库根目录 `CLAUDE.md`。

## 角色

FastAPI 0.115 + LangGraph + LangChain + OpenAI 兼容客户端。承担**所有 LLM 交互**：对话流式、文档嵌入、RAG 检索与生成、工具调用 Agent、工作流执行。仅由 Go 后端经内网调用，**不直接对客户端暴露**，**不读 MySQL**。依赖 `pyproject.toml` + `uv.lock`（`uv` 管理）。

## 常用命令

```bash
cp .env.example .env          # 首次必做，填 LLM key 与服务配置
uv sync                       # 按 uv.lock 安装依赖
uv add <pkg>                  # 新增依赖（同步更新 uv.lock）
uv run python main.py         # uvicorn 运行于 8001，自动重载
```

无 linter（`pyproject.toml` 未配 ruff/black）；如需格式化手动装后 `uv run ruff check .`。健康检查：`GET http://localhost:8001/health`。

## 关键 .env 变量

- **Chat LLM**：`LLM_API_KEY`、`LLM_API_BASE`、`LLM_MODEL`、`LLM_TIMEOUT`、`LLM_MAX_RETRIES`
- **Embedding（独立）**：`EMBEDDING_API_KEY`、`EMBEDDING_API_BASE`、`EMBEDDING_MODEL`。DeepSeek 无嵌入端点，需单独配 OpenAI 兼容商（推荐硅基流动 `BAAI/bge-m3`）；为空时自动回退 `LLM_*`。
- **基础设施**：`REDIS_HOST`、`CHROMA_HOST`、`CHROMA_COLLECTION_PREFIX`、`MINIO_*`、`MINIO_BUCKET`、`WEB_SEARCH_PROVIDER`、`TRACING_ENDPOINT`

FastAPI 用 **Redis DB 1**（Go 后端用 DB 0）。**切换嵌入模型后必须重建知识库索引**（旧向量与新模型不兼容）。嵌入临时文件用 `tempfile.gettempdir()`，跨平台（Windows 原生可跑）。

## 模块结构（`app/`）

- `chat/` —— LLM 调用、SSE 流式（`POST /chat`）；流尾补 `{type:'usage'}` 帧
- `rag/` —— 向量检索 + 答案生成（`POST /rag/chat`）。检索由 `graph.py` 的 **LangGraph StateGraph** 编排：`recall`（本地向量召回）与 `web`（联网搜索，按 `enable_web_search` 短路）并行 fan-out → `merge`（融合 + `rerank.py` 精排）产出 `sources`；生成在图外做 token 流式，保住「先发 sources 帧再逐 token」契约。`service.py` 负责上下文拼接 / 流式生成 / 引用对齐。注入顺序：`system_prompt`（角色/风格）→ RAG 引用规则+上下文 → history → 当前问题（引用规则离问题最近）。检索仅用当前 `question`，history 仅注入生成阶段。
- `embedding/` —— 切片、嵌入、写 ChromaDB（`POST /embedding/build`、`DELETE /embedding/delete`）。`service.py:_load_document` 按扩展名分发 loader：pdf→`PyPDFLoader`、doc/docx→`Docx2txtLoader`、xls/xlsx→自定义 `_load_excel`（xlsx 用 openpyxl 只读、xls 用 xlrd），其余按纯文本。**支持的扩展名须与后端 `config.yaml` 的 `doc_exts` 白名单及前端 `accept` 三处对齐**。按 `EMBED_BATCH_SIZE`(64) 分批嵌入 + 分批 upsert 防 OOM。
- `agent/` —— 工具调用 agent（`POST /agent/run`）：绑定 `HttpToolSpec` 构建的 HTTP 工具（`httpx` 执行）+ 经 `langchain-mcp-adapters`（`MultiServerMCPClient`）从 SSE MCP 加载的工具，跑有界 think→act 循环，返回答案与 `steps` 轨迹。MCP 导入做保护，缺库时服务仍可运行。
- `workflow/` —— 基于 LangGraph 的引擎（`POST /workflow/run`）。`engine.py` 拓扑执行画布图 JSON（节点 start/llm/http/search/end，输出以 id 存变量表供下游 `{{nodeId}}` 引用）；为空回退默认单节点 LLM。
- `llm/provider.py` —— LLM 提供方抽象（LangChain 接 OpenAI）。多模型路由：Go 把 `llm_config {api_base, api_key}` 随请求透传，按请求构建（LRU 缓存）客户端；未配置回退 `.env` 的 `LLM_*`。
- `vectorstore/chroma_client.py` —— ChromaDB HTTP 客户端
- `utils/web_search.py` —— 联网搜索层：provider 可插拔（`WEB_SEARCH_PROVIDER`，默认 duckduckgo，预留 tavily），统一三项防御（`asyncio.wait_for` 硬超时 / 正文截断 / 全异常 fail-open 降级）。暴露 `search_web_results`（结构化 `SourceDocument`，供 RAG 图与 workflow `search` 节点）与 `@tool web_search`（供 Chat 侧 tool-calling）
- `config/settings.py` —— Pydantic `BaseSettings`，从 `.env` 加载
- `api/router.py` —— 聚合各模块子路由，供 `main.py` 一次性挂载
- `tracing.py` —— OpenTelemetry（`FastAPIInstrumentor` + `HTTPXClientInstrumentor`），续接 Go 侧 W3C `traceparent`；`TRACING_ENDPOINT` 留空即 no-op

## SSE 契约

token 帧文本供前端 `extract` 取用；旁路帧 `{type:'sources'|'usage'|'title'|'status'}` 由前端 `onMeta` 分拣。RAG 必须先发 `sources` 帧再逐 token。所有流式响应经 Go 后端透传到浏览器。
