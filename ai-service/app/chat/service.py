"""对话业务逻辑：将请求消息转换为 LangChain 消息并以 SSE 流式返回 token。"""
import json
from typing import AsyncIterator
from langchain_core.messages import HumanMessage, AIMessage, SystemMessage
from app.llm.provider import get_chat_llm
from app.models.chat import ChatRequest, Message, SummarizeRequest
from app.observability import LlmCallTimer

_SUMMARIZE_PROMPT = """你是对话摘要助手。请把【已有摘要】与【新增对话】融合，生成一份更新后的简洁摘要，
保留关键事实、用户偏好、未决事项与结论，去除寒暄与冗余。用中文，控制在 300 字以内，只输出摘要正文。

【已有摘要】
{summary}

【新增对话】
{conversation}
"""


async def summarize(req: SummarizeRequest) -> str:
    """把已有摘要与本批旧消息融合为更新后的滚动摘要（P3-2 Memory 层）。"""
    cfg = req.llm_config
    llm = get_chat_llm(
        model=req.model,
        temperature=0.3,
        api_key=cfg.api_key if cfg else None,
        api_base=cfg.api_base if cfg else None,
    )
    conversation = "\n".join(f"{m.role}: {m.content}" for m in req.messages)
    prompt = _SUMMARIZE_PROMPT.format(summary=req.summary or "（无）", conversation=conversation)
    timer = LlmCallTimer("summarize", req.model)
    try:
        resp = await llm.ainvoke([HumanMessage(content=prompt)])
    except Exception as e:
        timer.fail(e)
        raise
    timer.done()
    return resp.content if isinstance(resp.content, str) else str(resp.content)


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
    cfg = req.llm_config
    llm = get_chat_llm(
        model=req.model,
        temperature=req.temperature,
        api_key=cfg.api_key if cfg else None,
        api_base=cfg.api_base if cfg else None,
    )
    lc_messages = [_to_lc_message(m) for m in req.messages]

    if getattr(req, "enable_web_search", False):
        from app.utils.web_search import web_search
        llm_runnable = llm.bind_tools([web_search])
    else:
        llm_runnable = llm

    timer = LlmCallTimer("chat", req.model)
    try:
        async for frame in _stream_tokens(llm_runnable, lc_messages, req, timer):

            yield frame
    except Exception as e:
        timer.fail(e)
        raise
    else:
        timer.done()

    # 结束哨兵，通知前端流式完成
    yield "data: [DONE]\n\n"


async def _stream_tokens(llm_runnable, lc_messages, req: ChatRequest, timer: LlmCallTimer):
    """逐 token 产出 SSE 帧；usage 帧夹带下发并喂给计时器。支持处理工具调用（如联网搜索）。"""
    from langchain_core.messages import ToolMessage
    max_turns = 3 if getattr(req, "enable_web_search", False) else 1

    for turn in range(max_turns):
        full_message = None
        async for chunk in llm_runnable.astream(lc_messages, stream_usage=True):
            if full_message is None:
                full_message = chunk
            else:
                full_message += chunk
                
            token = chunk.content
            if token and isinstance(token, str):
                data = json.dumps({"session_id": req.session_id, "token": token}, ensure_ascii=False)
                yield f"data: {data}\n\n"

            # 用量元数据通常仅在最后一个 chunk 上出现；与正文 token 解耦为独立帧，
            # 前端经 onMeta 旁路消费，不污染 Markdown 渲染。
            usage = getattr(chunk, "usage_metadata", None)
            if usage:
                timer.set_usage(usage)
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
                
        # 检查是否触发了工具调用
        if full_message and getattr(full_message, "tool_calls", None):
            lc_messages.append(full_message)
            from app.utils.web_search import web_search
            for tc in full_message.tool_calls:
                if tc["name"] == "web_search":
                    # 发送正在搜索的状态帧
                    yield f"data: {{\"type\": \"status\", \"session_id\": \"{req.session_id}\", \"content\": \"正在联网搜索...\"}}\n\n"
                    try:
                        result = await web_search.ainvoke(tc["args"])
                    except Exception as e:
                        result = f"搜索失败: {e}"
                    lc_messages.append(ToolMessage(content=str(result), tool_call_id=tc["id"]))
            # 进入下一轮循环，将搜索结果发给 LLM 生成最终回答
        else:
            break  # 无工具调用或已完成回答，退出循环
