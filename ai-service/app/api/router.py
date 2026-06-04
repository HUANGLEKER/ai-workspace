from fastapi import APIRouter
from app.chat.router import router as chat_router
from app.embedding.router import router as embedding_router
from app.rag.router import router as rag_router
from app.agent.router import router as agent_router
from app.workflow.router import router as workflow_router

api_router = APIRouter()

api_router.include_router(chat_router)
api_router.include_router(embedding_router)
api_router.include_router(rag_router)
api_router.include_router(agent_router)
api_router.include_router(workflow_router)
