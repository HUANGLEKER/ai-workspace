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

    # stream_usage=True：让 OpenAI 兼容端在流尾返回 token 用量（usage_metadata）。
    # 逐块消费 LLM 流式输出，将每个 token 包装为 SSE 帧；用量帧以独立 type=usage 帧夹带。
    async for chunk in llm.astream(lc_messages, stream_usage=True):
        token = chunk.content
        if token:
            data = json.dumps({"session_id": req.session_id, "token": token}, ensure_ascii=False)
            yield f"data: {data}\n\n"

        # 用量元数据通常仅在最后一个 chunk 上出现；与正文 token 解耦为独立帧，
        # 前端经 onMeta 旁路消费，不污染 Markdown 渲染。
        usage = getattr(chunk, "usage_metadata", None)
        if usage:
            usage_frame = json.dumps(
                {
                    "type": "usage",
                    "session_id": req.session_id,
                    "prompt_tokens": usage.get("input_tokens", 0),
                    "completion_tokens": usage.get("output_tokens", 0),
                    "total_tokens": usage.get("total_tokens", 0),
                },
                ensure_ascii=False,
            )
            yield f"data: {usage_frame}\n\n"

    # 结束哨兵，通知前端流式完成
    yield "data: [DONE]\n\n"
