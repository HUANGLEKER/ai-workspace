# AI Workspace

AI Workspace 是一个集成了聊天、知识库、RAG（检索增强生成）、提示词中心、工作流、Agent、MCP、工具中心、文件中心以及仪表盘于一体的个人 AI 工作平台。该项目旨在打造一个强大的个人 AI 中枢，以替代 ChatGPT + Dify + OpenWebUI + 部分 Notion AI 的组合。

## 🌟 核心特性

- **AI Chat**: 支持多模型对话，提供流式输出体验。
- **知识库 (Knowledge Base) & RAG**: 基于私有数据的检索增强生成引擎，支持上传文档并进行智能问答。
- **文件中心 (File Center)**: 基于 MinIO 的文件集中管理系统。
- **Agent (智能体)**: 可配置系统提示词与模型；运行时真实调用工具中心的 HTTP 工具，并通过 SSE 加载 MCP 服务器工具，返回完整执行轨迹。
- **工作流 (Workflow)**: 基于 LangGraph/LangChain 的工作流编排与运行。
- **工具中心 (Tool Center)**: HTTP / 内置工具注册表，供 Agent 运行时按需调用。
- **MCP 服务器**: MCP（Model Context Protocol）服务器注册表，支持 SSE 连通性检测与运行时工具加载。
- **提示词中心 (Prompt Center)**: 系统化的提示词模板管理，支持分类检索与一键复制。
- **定时任务 (Scheduled Jobs)**: 管理员可视化的动态 Cron 调度器，支持可插拔任务处理器与执行日志（管理员）。
- **系统监控 (Monitor)**: 服务器运行时指标（CPU/内存/磁盘/JVM）与依赖服务（Redis/FastAPI/MinIO）健康检查（管理员）。
- **仪表盘 (Dashboard)**: 可视化统计会话、知识库、文档与文件等概览。

## 🛠️ 技术栈

该项目采用主流的“前端 + Java后端 + Python AI 服务”三层架构：

| 层级 | 技术与框架 | 
| --- | --- |
| **前端 (Frontend)** | Vue3, TypeScript, Element Plus, Pinia, Vite |
| **后端 (Backend)** | Spring Boot 3.5.x, JDK 25, MyBatis Plus, JJWT, Spring Security |
| **AI 服务 (AI Service)** | FastAPI 0.115, LangGraph, LangChain, OpenAI API |
| **关系型数据库** | MySQL 8 |
| **向量数据库** | ChromaDB (用于RAG向量存储) |
| **缓存机制** | Redis 5 |
| **对象存储** | MinIO (用于文件中心) |

## 📐 系统架构

```text
┌──────────────────────────┐
│     Vue3 (Port: 3000)    │
└────────────┬─────────────┘
             │ HTTP (Axios/SSE)
             ▼
┌──────────────────────────┐
│ Spring Boot (Port: 8080) │
├──────────────────────────┤
│ Auth & RBAC              │
│ Chat / KB / File         │
│ Agent / Workflow         │
│ Prompt / Tool / MCP      │
│ Job & Monitor (admin)    │
└────────────┬─────────────┘
             │ HTTP Proxy
             ▼
┌──────────────────────────┐
│   FastAPI (Port: 8001)   │
├──────────────────────────┤
│ Chat / Streaming Engine  │
│ Embedding & RAG Engine   │
│ Agent (Tools + MCP)      │
│ Workflow Engine          │
└────────────┬─────────────┘
             │
 ┌───────────┼───────────┐
 ▼           ▼           ▼
Redis      ChromaDB     MinIO
             │
             ▼
            LLM
```

## 📁 目录结构

```text
ai-workspace/
├── ai-workspace-web/     # Vue3 前端项目
├── ai-workspace/         # Spring Boot 后端项目 (Maven 多模块)
│   ├── workspace-admin      # 主程序入口
│   ├── workspace-common     # 公共核心类
│   ├── workspace-framework  # 框架配置 (Security, Redis等)
│   ├── workspace-system     # RBAC 系统
│   ├── workspace-chat       # 会话管理
│   ├── workspace-kb         # 知识库
│   ├── workspace-file       # 文件中心
│   ├── workspace-agent      # Agent (CRUD + 运行时工具/MCP 调用)
│   ├── workspace-workflow   # 工作流
│   ├── workspace-prompt     # 提示词中心
│   ├── workspace-tool       # 工具中心
│   ├── workspace-mcp        # MCP 服务器注册表
│   ├── workspace-job        # 定时任务调度 (管理员)
│   └── workspace-monitor    # 仪表盘统计 + 系统监控
└── ai-service/           # FastAPI AI 服务
    ├── app/chat             # 聊天核心逻辑
    ├── app/rag              # RAG 检索生成
    ├── app/embedding        # 向量化处理
    ├── app/agent            # 工具调用 Agent (HTTP 工具 + MCP)
    └── app/workflow         # 工作流引擎
```

## 💻 环境要求 (Prerequisites)

为了在本地完整运行并开发这套 AI Workspace 系统，您需要准备以下环境：

- **Node.js**: v18 及以上版本 (前端运行环境)
- **JDK**: Java 25 (后端运行环境，需配置环境变量)
- **Python**: 3.10 及以上版本 (AI 服务运行环境，含 `pip`)
- **MySQL**: 8.x 版本 (核心业务数据库)
- **Redis**: 5.x 及以上版本 (缓存与会话管理)
- **ChromaDB**: 本地运行的向量数据库 (可通过 Python 或 Docker 启动)
- **MinIO**: 本地运行的对象存储服务
- **Git**: (可选) 用于代码版本控制

*💡 强烈推荐使用 [Docker Desktop](https://www.docker.com/products/docker-desktop/) 容器化一键部署 MySQL、Redis、ChromaDB 和 MinIO，以省去繁琐的本地配置过程。*

## 🚀 快速启动

### 1. 数据库准备
系统需要 MySQL, Redis, ChromaDB 和 MinIO 的支持。
```bash
# 导入初始化 SQL 脚本
mysql -u root -p < ai-workspace/sql/init.sql
```
*默认凭证*：
- MySQL 默认：`root / 123456`
- 平台管理员：`admin / admin123`

### 2. 启动前端项目 (Vue3)
```bash
cd ai-workspace-web
npm install
npm run dev
# 默认运行在 http://localhost:3000
```

### 3. 启动后端项目 (Spring Boot)
```bash
cd ai-workspace
# 编译并安装所有模块到本地仓库
./mvnw.cmd clean install -DskipTests
# 从入口模块 workspace-admin 启动服务
./mvnw.cmd -pl workspace-admin spring-boot:run
# 默认运行在 http://localhost:8080
```
> ⚠️ `spring-boot:run` 必须指定入口模块 `-pl workspace-admin`：根 `pom` 是聚合模块、无主类，直接在根目录运行会报 "Unable to find a suitable main class"。需先执行 `install` 让各子模块进入本地仓库。

*(配置文件位置：`workspace-admin/src/main/resources/application-dev.yml`)*

### 4. 启动 AI 服务 (FastAPI)
```bash
cd ai-service
cp .env.example .env
# 请在 .env 中填写您的 LLM_API_KEY 及其他配置信息
pip install -r requirements.txt
python main.py
# 默认运行在 http://localhost:8001
```

## 🐳 部署 MinIO 与 ChromaDB（缺失组件补齐）

当前机器仅有 **MySQL** 和 **Redis**，尚缺文件中心依赖的 **MinIO** 与 RAG 依赖的 **ChromaDB**。
缺少这两者不影响纯 Chat 与 RBAC，但文件上传/下载、知识库向量检索将不可用。

**推荐方案：用 Docker 单独部署这两个缺失组件**——零编译、隔离干净、随删随起。
MySQL 和 Redis 已在本机原生运行，**不要**再用容器重复启动，以免端口冲突。

### 1. 安装 Docker Desktop

安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)（Windows 自带 WSL2 后端），确认：

```powershell
docker version
```

### 2. 在项目根目录创建 `docker-compose.infra.yml`

```yaml
services:
  minio:
    image: minio/minio:latest
    container_name: ai-workspace-minio
    command: server /data --console-address ":9001"
    ports:
      - "9000:9000"   # S3 API（后端 / FastAPI 连接）
      - "9011:9001"   # Web 控制台（宿主机 9011，因本机 9001 被系统进程占用）
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    volumes:
      - minio-data:/data
    restart: unless-stopped

  chroma:
    image: chromadb/chroma:latest
    container_name: ai-workspace-chroma
    ports:
      - "8000:8000"
    volumes:
      - chroma-data:/chroma/chroma
    restart: unless-stopped

volumes:
  minio-data:
  chroma-data:
```

### 3. 启动并初始化

```powershell
docker compose -f docker-compose.infra.yml up -d
docker compose -f docker-compose.infra.yml ps
```

文件中心需要名为 `ai-workspace` 的存储桶，两种方式任选其一创建：

- **控制台**：打开 http://localhost:9011 （本机 9001 被系统进程占用，故控制台映射到宿主机 9011；S3 API 仍为 9000，不受影响），用 `minioadmin / minioadmin` 登录后新建桶 `ai-workspace`。
- **命令行**：

```powershell
docker run --rm --network host minio/mc sh -c "mc alias set local http://localhost:9000 minioadmin minioadmin && mc mb -p local/ai-workspace"
```

ChromaDB 无需手动建集合，FastAPI 首次写入时会按 `CHROMA_COLLECTION_PREFIX` 自动创建。

### 4. 健康检查

```powershell
curl http://localhost:9000/minio/health/live      # MinIO
curl http://localhost:8000/api/v2/heartbeat        # ChromaDB
```

### 5. 配置 `ai-service/.env`

由 `.env.example` 复制后保持以下默认值即可对接上面的容器：

```dotenv
CHROMA_HOST=localhost
CHROMA_PORT=8000
CHROMA_COLLECTION_PREFIX=ai_workspace

MINIO_ENDPOINT=localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=ai-workspace
MINIO_SECURE=false
```

> **备选（不使用 Docker）**：MinIO 可从 https://min.io/download 下载 `minio.exe` 运行
> `.\minio.exe server D:\minio-data --console-address ":9011"`（本机 9001 被系统进程占用）；ChromaDB 可 `pip install chromadb`
> 后运行 `chroma run --host 0.0.0.0 --port 8000 --path D:\chroma-data`（建议放独立虚拟环境避免依赖冲突）。

> **数据持久化**：容器数据保存在 Docker 卷 `minio-data` / `chroma-data`，`docker compose down` 不加 `-v` 不会丢数据。

## 🐍 Python 依赖安装（已适配 Python 3.14）

`ai-service` 的 `requirements.txt` 已升级并**锁定为在 Python 3.14 上验证可运行的版本**。
相比最初基线,这是一次大版本升级:langchain `0.3.x → 1.3.x`、chromadb `0.6.x → 1.5.x`、
langgraph `0.2.x → 1.2.x`、fastapi/pydantic 等同步升级。3.14 上所有包均有预编译 wheel,无需本地编译。

### 安装步骤

```powershell
cd "C:\Users\leker\Desktop\AI Workspace\ai-service"

# 1. 用 Python 3.14 创建虚拟环境
python -m venv .venv

# 2. 激活（若报执行策略错误，先跑一次下面被注释的命令）
.\.venv\Scripts\Activate.ps1
# Set-ExecutionPolicy -Scope CurrentUser RemoteSigned

# 3. 升级 pip 并安装
python -m pip install --upgrade pip
pip install -r requirements.txt

# 4. 配置环境变量
copy .env.example .env   # 填入 LLM_API_KEY 等

# 5. 启动
python main.py           # http://localhost:8001
```

验证:`curl http://localhost:8001/health` 返回 `{"code":200,"message":"ok"}`。

### 升级带来的代码变更

langchain 1.x 拆分了文本分割模块,已相应修改:

- `app/embedding/service.py`:`from langchain.text_splitter ...` → `from langchain_text_splitters import RecursiveCharacterTextSplitter`

### 注意事项

- **客户端版本对齐**:chromadb 客户端为 `1.5.9`,与 Docker 镜像 `chromadb/chroma:latest`(1.x)匹配,
  心跳端点为 `/api/v2/heartbeat`。
- **运行期功能需实测**:上述已验证服务可正常启动、所有模块可加载。但 RAG / embedding / agent
  链路涉及 langchain 1.x、chromadb 1.x 的运行时行为,建议接好 LLM Key 与 MinIO/ChromaDB 容器后,
  实测「上传文档 → 向量化 → RAG 问答」全链路;如遇 1.x API 差异,可能需要少量适配。
- **MCP 依赖**:Agent 运行时通过 `langchain-mcp-adapters` + `mcp` 加载 SSE MCP 服务器工具,二者已加入
  `requirements.txt`。该能力仅在 AI 服务运行环境(WSL/Linux/Docker)生效,且首次实跑建议复核
  `MultiServerMCPClient` 的 API 与锁定版本;Agent 的 HTTP 工具调用基于已内置的 `httpx`,无额外依赖。

## 📝 开发进度表 (Roadmap)

- [x] **Sprint 1**: 基础系统搭建、Spring Boot 及 Vue3 初始化、JWT 认证与 RBAC 权限。
- [x] **Sprint 2**: AI 会话模块开发，支持 SSE 流式输出与 Markdown 渲染。
- [x] **Sprint 3**: 文件中心 (MinIO) 对接，知识库基础 CRUD 开发。
- [x] **Sprint 4**: RAG 引擎上线，支持文档解析、Chunk 切片、Embedding 向量化与问答。
- [x] **Sprint 5**: 数据看板 (Dashboard) 完成统计与概览开发。
- [x] **Sprint 6**: 系统监控 (Monitor) 上线；Agent 与工作流模块（CRUD + 运行代理至 FastAPI）；动态 Cron 定时任务调度器 (Job)。
- [x] **Sprint 7**: 提示词中心 (Prompt)、工具中心 (Tool)、MCP 服务器注册表上线（用户级 CRUD，含 MCP 连通性检测）。
- [x] **Sprint 8**: Agent 运行时工具联动——真实 HTTP 工具执行 + SSE MCP 工具加载，贯通 Spring → FastAPI 工具调用循环；前端可选择工具/MCP 并展示执行轨迹。

## 📄 许可证

Personal Use.
