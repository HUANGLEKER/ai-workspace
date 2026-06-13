"""RAG 知识库问答业务逻辑（P2-3：召回 → rerank 精排 → 引用对齐）。

流程：问题向量化 → 向量召回 recall_k 个候选 → reranker 精排取 top_k →
拼为带编号的上下文注入系统提示 → 流式生成答案。
SSE 先发一帧 sources 元数据（含 rerank 分数与引用标记），随后逐 token 发答案。
"""
import json
import re
from typing import AsyncIterator
from langchain_core.messages import SystemMessage, HumanMessage
from app.config.settings import settings
from app.llm.provider import get_chat_llm, get_embeddings
from app.models.rag import RagChatRequest, SourceDocument
from app.observability import LlmCallTimer
from app.rag.rerank import rerank
from app.utils.safety import INJECTION_GUARD, fence
from app.vectorstore.chroma_client import get_or_create_collection

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


async def _recall(req: RagChatRequest) -> list[SourceDocument]:
    """向量召回候选切片（rerank 启用时召回 recall_k 个，否则召回 top_k 个）。"""
    embeddings = get_embeddings()
    query_vector = await embeddings.aembed_query(req.question)

    recall_k = settings.rerank_recall_k if settings.rerank_enabled else req.top_k
    collection = get_or_create_collection(req.kb_id)
    results = collection.query(
        query_embeddings=[query_vector],  # type: ignore
        n_results=recall_k,
        include=["documents", "metadatas", "distances"],
    )

    sources: list[SourceDocument] = []
    documents = results.get("documents")
    if documents and documents[0]:
        doc_list = documents[0]
        metadatas = results.get("metadatas")
        distances = results.get("distances")
        meta_list = metadatas[0] if metadatas else [{}] * len(doc_list)
        dist_list = distances[0] if distances else [0.0] * len(doc_list)
        for doc, meta, dist in zip(doc_list, meta_list, dist_list):
            sources.append(SourceDocument(
                document_id=str(meta.get("document_id", "")) if meta else "",
                file_name=str(meta.get("file_name", "")) if meta else "",
                content=doc,
                score=round(1 - dist, 4),
            ))
    return sources


async def _retrieve(req: RagChatRequest) -> list[SourceDocument]:
    """召回 + rerank 精排，返回最终 top_k 条来源（保留 rerank 分数）。"""
    candidates = await _recall(req)
    if not candidates or not settings.rerank_enabled:
        return candidates[:req.top_k]

    order = await rerank(req.question, [s.content for s in candidates], req.top_k)
    reranked: list[SourceDocument] = []
    for rank, idx in enumerate(order):
        s = candidates[idx]
        # rerank 后用 1/(rank+1) 作为展示用的相对精排分数（降序）
        s.rerank_score = round(1.0 / (rank + 1), 4)
        reranked.append(s)
    return reranked


def _sources_frame(session_id: str, sources: list[SourceDocument]) -> str:
    """构造 sources 元数据 SSE 帧。"""
    payload = json.dumps(
        {"type": "sources", "session_id": session_id, "sources": [s.model_dump() for s in sources]},
        ensure_ascii=False,
    )
    return f"data: {payload}\n\n"


async def stream_rag_chat(req: RagChatRequest) -> AsyncIterator[str]:
    """检索增强问答：召回精排 → 下发来源 → 流式生成 → 回填引用对齐。"""
    sources = await _retrieve(req)

    # 带编号拼接上下文，编号与 sources 顺序一一对应（从 1 开始）；
    # 文档正文用不可信数据分隔符包裹（注入防护），编号/文件名等可信元数据在栅栏外
    if sources:
        context = "\n\n---\n\n".join(
            f"[来源{i + 1}] ({s.file_name})\n{fence(s.content)}" for i, s in enumerate(sources)
        )
    else:
        context = "暂无相关文档"
    system_msg = SystemMessage(content=_RAG_SYSTEM_PROMPT.format(context=context))
    human_msg = HumanMessage(content=req.question)

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
    timer = LlmCallTimer("rag", req.model)
    try:
        async for chunk in llm.astream([system_msg, human_msg], stream_usage=True):
            usage = getattr(chunk, "usage_metadata", None)
            if usage:
                timer.set_usage(usage)
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

    # 引用对齐：从完整答案提取 [来源N] 标记，回填 cited 后重发 sources 帧
    cited_idx = {int(m) - 1 for m in _CITE_RE.findall("".join(answer_parts))}
    if cited_idx:
        for i, s in enumerate(sources):
            s.cited = i in cited_idx
        yield _sources_frame(req.session_id, sources)

    yield "data: [DONE]\n\n"
