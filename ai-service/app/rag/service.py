"""RAG 知识库问答业务逻辑（P2-3：召回 → rerank 精排 → 引用对齐）。

流程：检索编排图（本地向量召回 + 可选联网搜索 → 多路融合 + rerank 精排）→
拼为带编号的上下文注入系统提示 → 流式生成答案。
SSE 先发一帧 sources 元数据（含 rerank 分数与引用标记），随后逐 token 发答案。

检索部分（召回/联网/精排）已抽到 app/rag/graph.py 的 LangGraph StateGraph 编排；
本模块只负责图外的「上下文拼接 + 流式生成 + 引用对齐」，以保住 SSE 帧顺序契约。
"""
import json
import re
from typing import AsyncIterator
from langchain_core.messages import SystemMessage, HumanMessage, AIMessage, BaseMessage
from app.llm.provider import get_chat_llm
from app.models.rag import RagChatRequest, SourceDocument
from app.observability import LlmCallTimer
from app.rag.graph import retrieve
from app.utils.safety import INJECTION_GUARD, fence

# 用带编号的来源块，并要求模型在引用时标注 [来源N]，便于回填引用对齐
_RAG_SYSTEM_PROMPT = """你是一个知识库问答助手。请根据以下带编号的参考文档回答用户的问题。
引用某条参考文档的内容时，请在句末标注其编号，格式为 [来源N]（N 为文档编号）。
如果参考文档中没有相关信息，请如实告知用户，不要编造答案。
""" + INJECTION_GUARD + """

参考文档：
{context}
"""

# 匹配答案中的 [来源N] / [来源 N] 引用标记
_CITE_RE = re.compile(r"\[来源\s*(\d+)\]")


def _sources_frame(session_id: str, sources: list[SourceDocument]) -> str:
    """构造 sources 元数据 SSE 帧。"""
    payload = json.dumps(
        {"type": "sources", "session_id": session_id, "sources": [s.model_dump() for s in sources]},
        ensure_ascii=False,
    )
    return f"data: {payload}\n\n"


async def stream_rag_chat(req: RagChatRequest) -> AsyncIterator[str]:
    """检索增强问答：检索图（召回+联网+精排）→ 下发来源 → 流式生成 → 回填引用对齐。"""
    sources = await retrieve(req)

    # 带编号拼接上下文，编号与 sources 顺序一一对应（从 1 开始）；
    # 文档正文用不可信数据分隔符包裹（注入防护），编号/文件名等可信元数据在栅栏外
    if sources:
        context = "\n\n---\n\n".join(
            f"[来源{i + 1}] ({s.file_name})\n{fence(s.content)}" for i, s in enumerate(sources)
        )
    else:
        context = "暂无相关文档"
    system_msg = SystemMessage(content=_RAG_SYSTEM_PROMPT.format(context=context))
    # 多轮上下文：历史消息注入到 system 与当前问题之间，让模型理解追问指代。
    # 检索阶段仍只用当前 question（见上 retrieve(req)），历史不参与召回。
    history_msgs: list[BaseMessage] = [
        AIMessage(content=m.content) if m.role == "assistant" else HumanMessage(content=m.content)
        for m in req.history
        if m.content
    ]
    human_msg = HumanMessage(content=req.question)
    llm_messages: list[BaseMessage] = [system_msg, *history_msgs, human_msg]

    cfg = req.llm_config
    llm = get_chat_llm(
        model=req.model,
        temperature=req.temperature,
        api_key=cfg.api_key if cfg else None,
        api_base=cfg.api_base if cfg else None,
    )

    # 先发一版 sources 帧（cited 暂为 False），答案结束后若有引用再发修订版
    yield _sources_frame(req.session_id, sources)

    # 逐 token 下发的同时缓冲完整答案，用于结束后的引用对齐
    answer_parts: list[str] = []
    last_usage = None
    timer = LlmCallTimer("rag", req.model)
    try:
        async for chunk in llm.astream(llm_messages, stream_usage=True):
            usage = getattr(chunk, "usage_metadata", None)
            if usage:
                timer.set_usage(usage)
                last_usage = usage
            token = chunk.content
            if token:
                answer_parts.append(token)
                data = json.dumps({"type": "token", "session_id": req.session_id, "token": token}, ensure_ascii=False)
                yield f"data: {data}\n\n"
    except Exception as e:
        timer.fail(e)
        raise
    else:
        timer.done()

    # 用量帧：与 chat 流对齐，便于前端展示 token 统计、Go 侧随消息落库
    if last_usage:
        usage_frame = json.dumps(
            {
                "type": "usage",
                "session_id": req.session_id,
                "prompt_tokens": last_usage.get("input_tokens", 0),
                "completion_tokens": last_usage.get("output_tokens", 0),
                "total_tokens": last_usage.get("total_tokens", 0),
            },
            ensure_ascii=False,
        )
        yield f"data: {usage_frame}\n\n"

    # 引用对齐：从完整答案提取 [来源N] 标记，回填 cited 后重发 sources 帧
    cited_idx = {int(m) - 1 for m in _CITE_RE.findall("".join(answer_parts))}
    if cited_idx:
        for i, s in enumerate(sources):
            s.cited = i in cited_idx
        yield _sources_frame(req.session_id, sources)

    yield "data: [DONE]\n\n"
