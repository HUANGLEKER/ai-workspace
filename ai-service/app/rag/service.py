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
    embeddings = get_embeddings()
    query_vector = await embeddings.aembed_query(req.question)

    collection = get_or_create_collection(req.kb_id)
    results = collection.query(
        query_embeddings=[query_vector],  # type: ignore
        n_results=req.top_k,
        include=["documents", "metadatas", "distances"],
    )

    sources: list[SourceDocument] = []
    context_parts: list[str] = []
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
            context_parts.append(f"[{meta.get('file_name', '') if meta else ''}]\n{doc}")

    context = "\n\n---\n\n".join(context_parts) if context_parts else "暂无相关文档"
    system_msg = SystemMessage(content=_RAG_SYSTEM_PROMPT.format(context=context))
    human_msg = HumanMessage(content=req.question)

    # first send sources metadata
    sources_data = json.dumps(
        {"type": "sources", "session_id": req.session_id, "sources": [s.model_dump() for s in sources]},
        ensure_ascii=False,
    )
    yield f"data: {sources_data}\n\n"

    llm = get_chat_llm(model=req.model, temperature=req.temperature)
    async for chunk in llm.astream([system_msg, human_msg]):
        token = chunk.content
        if token:
            data = json.dumps({"type": "token", "session_id": req.session_id, "token": token}, ensure_ascii=False)
            yield f"data: {data}\n\n"

    yield "data: [DONE]\n\n"
