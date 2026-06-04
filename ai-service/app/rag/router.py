from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from app.models.rag import RagChatRequest
from app.rag.service import stream_rag_chat

router = APIRouter(prefix="/rag", tags=["RAG"])


@router.post("/chat")
async def rag_chat(req: RagChatRequest):
    return StreamingResponse(
        stream_rag_chat(req),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
