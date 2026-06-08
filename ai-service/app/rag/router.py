"""RAG 路由：暴露 POST /rag/chat，以 SSE 流式返回带来源引用的问答结果。"""
from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from app.models.rag import RagChatRequest
from app.rag.service import stream_rag_chat

router = APIRouter(prefix="/rag", tags=["RAG"])


@router.post("/chat")
async def rag_chat(req: RagChatRequest):
    # 返回 SSE 流：首帧为来源元数据，后续为答案 token
    return StreamingResponse(
        stream_rag_chat(req),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
