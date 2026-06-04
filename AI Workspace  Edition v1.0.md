# AI Workspace  Edition v1.0

## 

> Version: 1.0.0
>  Architecture: Monolithic Modular Architecture
>  Development Mode: AI Vibe Coding + AI Review
>  Target: Personal
>  Tech Stack:
>
> - Frontend：Vue3 + TypeScript + Element Plus
> - Backend：Spring Boot 3.5.x + JDK25
> - AI Service：FastAPI + LangGraph + LangChain
> - Database：MySQL 8
> - Cache：Redis 5
> - Vector DB：ChromaDB
> - Storage：MinIO
> - Deploy：本地部署

------

# 一、项目定位

## 产品定义

AI Workspace 是一个集成：

```
AI Chat
Knowledge Base
RAG
Prompt Center
Workflow
Agent
MCP
Tool Center
File Center
Dashboard
```

于一体的 AI 工作平台。

------

## 产品目标

替代：

```
ChatGPT
+
Dify
+
OpenWebUI
+
部分Notion AI
```

打造个人AI 中枢。

------

# 二、总体架构

```
┌──────────────────────────┐
│         Vue3             │
└────────────┬─────────────┘
             │
             ▼

┌──────────────────────────┐
│      Spring Boot         │
├──────────────────────────┤
│ Auth                     │
│ RBAC                     │
│ Chat                     │
│ Knowledge Base           │
│ File Center              │
│ Dashboard                │
│ Workflow                 │
│ Agent                    │
└────────────┬─────────────┘
             │ HTTP
             ▼

┌──────────────────────────┐
│        FastAPI           │
├──────────────────────────┤
│ Chat Engine              │
│ Embedding Engine         │
│ RAG Engine               │
│ Agent Engine             │
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

------

# 三、项目目录结构

## Frontend

```
ai-workspace-web

src

├── api
├── assets
├── components
├── directives
├── hooks
├── layout

├── router
├── store

├── views

│   ├── login
│   ├── dashboard

│   ├── chat

│   ├── knowledge
│   │   ├── base
│   │   ├── document
│   │   └── rag

│   ├── file

│   ├── prompt

│   ├── workflow

│   ├── agent

│   ├── monitor

│   └── system

├── utils
└── types
```

------

## Spring Boot

```
ai-workspace

├── workspace-admin

├── workspace-common
│   ├── core
│   ├── exception
│   ├── response
│   ├── utils
│   └── constant

├── workspace-framework
│   ├── security
│   ├── redis
│   ├── log
│   ├── swagger
│   └── web

├── workspace-system

├── workspace-chat

├── workspace-kb

├── workspace-file

├── workspace-agent

├── workspace-workflow

├── workspace-monitor

└── workspace-job
```

------

## FastAPI

```
ai-service

app

├── api

├── chat

├── rag

├── embedding

├── vectorstore

├── workflow

├── agent

├── llm

├── models

├── config

└── utils
```

------

# 四、数据库设计

------

# 1. 用户体系

## sys_user

```
CREATE TABLE sys_user(
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) UNIQUE,
    password VARCHAR(255),
    nickname VARCHAR(50),
    avatar VARCHAR(500),
    email VARCHAR(100),
    phone VARCHAR(20),
    status TINYINT DEFAULT 1,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME
);
```

------

## sys_role

```
CREATE TABLE sys_role(
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_name VARCHAR(50),
    role_code VARCHAR(50),
    remark VARCHAR(255)
);
```

------

## sys_menu

```
CREATE TABLE sys_menu(
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT,
    menu_name VARCHAR(50),
    path VARCHAR(200),
    component VARCHAR(200),
    permission VARCHAR(100),
    menu_type CHAR(1)
);
```

------

## sys_user_role

```
CREATE TABLE sys_user_role(
    user_id BIGINT,
    role_id BIGINT
);
```

------

## sys_role_menu

```
CREATE TABLE sys_role_menu(
    role_id BIGINT,
    menu_id BIGINT
);
```

------

# 2. Chat模块

## chat_session

```
CREATE TABLE chat_session(
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    title VARCHAR(255),
    model_name VARCHAR(100),
    create_time DATETIME
);
```

------

## chat_message

```
CREATE TABLE chat_message(
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT,
    role VARCHAR(20),
    content LONGTEXT,
    token_count INT,
    create_time DATETIME
);
```

------

## chat_model

```
CREATE TABLE chat_model(
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    model_name VARCHAR(100),
    provider VARCHAR(50),
    api_url VARCHAR(500),
    api_key VARCHAR(500)
);
```

------

# 3. 知识库模块

## kb_knowledge_base

```
CREATE TABLE kb_knowledge_base(
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_name VARCHAR(100),
    description TEXT,
    create_by BIGINT,
    create_time DATETIME
);
```

------

## kb_document

```
CREATE TABLE kb_document(
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    kb_id BIGINT,
    file_name VARCHAR(255),
    file_path VARCHAR(500),
    status VARCHAR(20),
    create_time DATETIME
);
```

------

## kb_chunk_task

```
CREATE TABLE kb_chunk_task(
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT,
    task_status VARCHAR(20),
    create_time DATETIME
);
```

------

# 4. 文件中心

## file_info

```
CREATE TABLE file_info(
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    file_name VARCHAR(255),
    file_path VARCHAR(500),
    file_size BIGINT,
    file_type VARCHAR(50),
    create_time DATETIME
);
```

------

# 五、REST API规范

## Auth

```
POST /api/auth/login

POST /api/auth/logout

GET /api/auth/info
```

------

## User

```
GET /api/user/page

POST /api/user/add

PUT /api/user/update

DELETE /api/user/delete/{id}
```

------

## Chat

```
GET /api/chat/session/list

POST /api/chat/session/create

DELETE /api/chat/session/delete

POST /api/chat/send
```

------

## KB

```
GET /api/kb/list

POST /api/kb/create

PUT /api/kb/update

DELETE /api/kb/delete
```

------

## Document

```
POST /api/document/upload

GET /api/document/list

DELETE /api/document/delete
```

------

## RAG

```
POST /api/rag/chat

POST /api/rag/rebuild
```

------

# 六、前端页面原型

## Dashboard

```
┌─────────────────────┐
│ 今日会话数          │
│ Token消耗           │
│ 知识库数量          │
│ 文档数量            │
└─────────────────────┘
```

------

## Chat

```
┌────────────┬─────────────┐
│ 会话列表   │ 对话窗口     │
│            │             │
│ Session1   │ User        │
│ Session2   │ AI          │
│ Session3   │             │
└────────────┴─────────────┘
```

------

## Knowledge Base

```
知识库列表

+ Java知识库
+ Spring知识库
+ Redis知识库
```

------

# 七、FastAPI设计

## Chat Service

```
POST /chat
```

负责：

```
模型调用
上下文管理
流式输出
```

------

## RAG Service

```
POST /rag/chat
```

负责：

```
向量检索
Prompt构造
答案生成
```

------

## Embedding Service

```
POST /embedding/build
```

负责：

```
切片
Embedding
写入ChromaDB
```

------

# 八、WBS开发任务

## Sprint1

### 系统基础

-  Spring Boot初始化
-  Vue3初始化
-  JWT认证
-  RBAC

------

## Sprint2

### Chat

-  会话管理
-  SSE流式输出
-  Markdown渲染

------

## Sprint3

### 文件中心

-  文件上传
-  文件下载
-  文件预览

### 知识库

-  知识库CRUD
-  文档管理

------

## Sprint4

### RAG

-  文档解析
-  Chunk
-  Embedding
-  ChromaDB

------

## Sprint5

### Dashboard

-  数据统计
-  最近记录

------

# 十、V1.0验收标准

必须完成：

```
√ 登录认证

√ RBAC权限

√ AI聊天

√ 会话管理

√ 文件上传

√ 知识库管理

√ 文档管理

√ RAG问答

√ Dashboard
```