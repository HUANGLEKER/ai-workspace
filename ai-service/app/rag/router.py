"""RAG 路由：暴露 POST /rag/chat，以 SSE 流式返回带来源引用的问答结果。"""
from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from app.models.rag import RagChatRequest
from app.rag.service import stream_rag_chat

router = APIRouter(prefix="/rag", tags=["RAG"])


@router.post("/chat")
async def rag_chat(req: RagChatRequest):
    """
    POST /rag/chat — RAG 流式问答。

    返回 SSE 流，包含两种帧：
    - 首帧：``{"type":"sources", "sources":[...]}`` 来源元数据
    - 后续帧：``{"type":"token", "token":"..."}`` 答案 token，以 [DONE] 结束
    """
    # X-Accel-Buffering=no 关闭 Nginx 等反向代理缓冲，保证 token 实时推送
    return StreamingResponse(
        stream_rag_chat(req),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
