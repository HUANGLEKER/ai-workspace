"""对话路由：暴露 POST /chat，以 text/event-stream 形式流式返回回复。"""
from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from app.models.chat import ChatRequest
from app.chat.service import stream_chat

router = APIRouter(prefix="/chat", tags=["Chat"])


@router.post("")
async def chat(req: ChatRequest):
    """
    POST /chat — 流式对话。

    返回 SSE 流，每帧格式为 ``{"session_id":"...","token":"..."}``，以 ``[DONE]`` 结束。
    X-Accel-Buffering=no 关闭反向代理缓冲，确保 token 实时推送到客户端。
    """
    return StreamingResponse(
        stream_chat(req),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
