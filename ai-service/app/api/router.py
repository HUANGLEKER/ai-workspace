"""聚合路由：将各业务模块的子路由统一汇入，供 main.py 一次性挂载。"""
from fastapi import APIRouter
from app.chat.router import router as chat_router
from app.embedding.router import router as embedding_router
from app.rag.router import router as rag_router
from app.agent.router import router as agent_router
from app.workflow.router import router as workflow_router

api_router = APIRouter()

# 依次注册各功能模块路由
api_router.include_router(chat_router)       # /chat —— 对话流式
api_router.include_router(embedding_router)  # /embedding —— 文档嵌入
api_router.include_router(rag_router)        # /rag —— 知识库问答
api_router.include_router(agent_router)      # /agent —— 工具调用 Agent
api_router.include_router(workflow_router)   # /workflow —— 工作流
