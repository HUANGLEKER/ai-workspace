"""对话路由：流式对话 POST /chat 与会话摘要 POST /chat/summarize。"""
from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from app.models.chat import ChatRequest, SummarizeRequest
from app.chat.service import stream_chat, summarize
from app.utils.response import Result

router = APIRouter(prefix="/chat", tags=["Chat"])


@router.post("/summarize", response_model=Result)
async def summarize_route(req: SummarizeRequest):
    """会话滚动摘要（P3-2）：返回融合后的摘要文本，Go 侧落库到 chat_session.summary。"""
    text = await summarize(req)
    return Result.ok(data={"summary": text})


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
