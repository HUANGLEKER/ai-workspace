"""对话业务逻辑：将请求消息转换为 LangChain 消息并以 SSE 流式返回 token。"""
import json
from typing import AsyncIterator
from langchain_core.messages import HumanMessage, AIMessage, SystemMessage
from app.llm.provider import get_chat_llm
from app.models.chat import ChatRequest, Message


def _to_lc_message(msg: Message):
    """将自定义 Message 按 role 映射为对应的 LangChain 消息类型。"""
    if msg.role == "user":
        return HumanMessage(content=msg.content)
    elif msg.role == "assistant":
        return AIMessage(content=msg.content)
    else:
        return SystemMessage(content=msg.content)


async def stream_chat(req: ChatRequest) -> AsyncIterator[str]:
    """流式生成对话回复，逐 token 产出 SSE 数据帧，结尾以 [DONE] 哨兵收尾。"""
    llm = get_chat_llm(model=req.model, temperature=req.temperature)
    lc_messages = [_to_lc_message(m) for m in req.messages]

    # 逐块消费 LLM 流式输出，将每个 token 包装为 SSE 帧
    async for chunk in llm.astream(lc_messages):
        token = chunk.content
        if token:
            data = json.dumps({"session_id": req.session_id, "token": token}, ensure_ascii=False)
            yield f"data: {data}\n\n"

    # 结束哨兵，通知前端流式完成
    yield "data: [DONE]\n\n"
