from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from app.models.chat import ChatRequest
from app.chat.service import stream_chat

router = APIRouter(prefix="/chat", tags=["Chat"])


@router.post("")
async def chat(req: ChatRequest):
    return StreamingResponse(
        stream_chat(req),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
