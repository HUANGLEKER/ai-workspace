"""RAG 知识库问答业务逻辑。

流程：将问题向量化 → 在知识库集合中检索 top_k 相关切片 → 拼为上下文注入系统提示 →
流式生成答案。SSE 先发送一帧 sources 元数据（来源引用），随后逐 token 发送答案。
"""
import json
from typing import AsyncIterator
from langchain_core.messages import SystemMessage, HumanMessage
from app.llm.provider import get_chat_llm, get_embeddings
from app.models.rag import RagChatRequest, SourceDocument
from app.vectorstore.chroma_client import get_or_create_collection

_RAG_SYSTEM_PROMPT = """你是一个知识库问答助手。请根据以下参考文档回答用户的问题。
如果参考文档中没有相关信息，请如实告知用户，不要编造答案。

参考文档：
{context}
"""


async def stream_rag_chat(req: RagChatRequest) -> AsyncIterator[str]:
    """检索增强问答：先检索来源并下发，再基于上下文流式生成答案。"""
    # 1) 问题向量化
    embeddings = get_embeddings()
    query_vector = await embeddings.aembed_query(req.question)

    # 2) 在知识库集合中向量检索 top_k 切片
    collection = get_or_create_collection(req.kb_id)
    results = collection.query(
        query_embeddings=[query_vector],  # type: ignore
        n_results=req.top_k,
        include=["documents", "metadatas", "distances"],
    )

    # 3) 组装来源引用列表与用于注入提示的上下文文本
    sources: list[SourceDocument] = []
    context_parts: list[str] = []
    documents = results.get("documents")
    if documents and documents[0]:
        doc_list = documents[0]
        metadatas = results.get("metadatas")
        distances = results.get("distances")
        # 元数据/距离可能缺失，用占位列表对齐长度以便 zip
        meta_list = metadatas[0] if metadatas else [{}] * len(doc_list)
        dist_list = distances[0] if distances else [0.0] * len(doc_list)
        for doc, meta, dist in zip(doc_list, meta_list, dist_list):
            sources.append(SourceDocument(
                document_id=str(meta.get("document_id", "")) if meta else "",
                file_name=str(meta.get("file_name", "")) if meta else "",
                content=doc,
                score=round(1 - dist, 4),  # 余弦距离转相似度分数
            ))
            context_parts.append(f"[{meta.get('file_name', '') if meta else ''}]\n{doc}")

    # 无命中时给出占位上下文，提示模型如实告知
    context = "\n\n---\n\n".join(context_parts) if context_parts else "暂无相关文档"
    system_msg = SystemMessage(content=_RAG_SYSTEM_PROMPT.format(context=context))
    human_msg = HumanMessage(content=req.question)

    # 4) 先发送来源元数据帧（前端渲染为可折叠的来源引用）
    sources_data = json.dumps(
        {"type": "sources", "session_id": req.session_id, "sources": [s.model_dump() for s in sources]},
        ensure_ascii=False,
    )
    yield f"data: {sources_data}\n\n"

    # 5) 基于上下文流式生成答案，逐 token 以 type=token 帧下发
    cfg = req.llm_config
    llm = get_chat_llm(
        model=req.model,
        temperature=req.temperature,
        api_key=cfg.api_key if cfg else None,
        api_base=cfg.api_base if cfg else None,
    )
    async for chunk in llm.astream([system_msg, human_msg]):
        token = chunk.content
        if token:
            data = json.dumps({"type": "token", "session_id": req.session_id, "token": token}, ensure_ascii=False)
            yield f"data: {data}\n\n"

    # 结束哨兵
    yield "data: [DONE]\n\n"
