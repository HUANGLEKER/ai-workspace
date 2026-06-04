import json
from typing import AsyncIterator
from langchain_core.messages import HumanMessage, AIMessage, SystemMessage
from app.llm.provider import get_chat_llm
from app.models.chat import ChatRequest, Message


def _to_lc_message(msg: Message):
    if msg.role == "user":
        return HumanMessage(content=msg.content)
    elif msg.role == "assistant":
        return AIMessage(content=msg.content)
    else:
        return SystemMessage(content=msg.content)


async def stream_chat(req: ChatRequest) -> AsyncIterator[str]:
    llm = get_chat_llm(model=req.model, temperature=req.temperature)
    lc_messages = [_to_lc_message(m) for m in req.messages]

    async for chunk in llm.astream(lc_messages):
        token = chunk.content
        if token:
            data = json.dumps({"session_id": req.session_id, "token": token}, ensure_ascii=False)
            yield f"data: {data}\n\n"

    yield "data: [DONE]\n\n"
