"""RAG 检索编排图（方案 C：LangGraph StateGraph）。

把「本地向量召回」与「联网搜索」编排为一张显式有向图，统一在 merge 节点做多路融合 + rerank
精排，产出最终 sources。相比此前散在 service 里的 if/gather，图编排带来：
  - 并行 fan-out：recall 与 web 两路并发执行（写入不同 state key，无竞争），延迟取较慢一路。
  - 条件降级：web 节点内部按 enable_web_search 开关短路；web 失败由 search_web_results fail-open 兜底。
  - 可扩展：后续加 query 改写 / 多知识库 / 答案校验节点，只需在图上插点连边。

图只负责「检索」，不负责「生成」——生成仍由 service.stream_rag_chat 在图外做 token 流式，
以保证 SSE 先发 sources 帧、再逐 token 下发的既有契约不变（流式生成嵌进图会牺牲这一可控性）。

    START ─┬─▶ recall ─┐
           └─▶ web ────┴─▶ merge ─▶ END
"""
from typing import Optional, TypedDict

from langgraph.graph import END, START, StateGraph

from app.config.settings import settings
from app.llm.provider import get_embeddings
from app.models.rag import RagChatRequest, SourceDocument
from app.rag.rerank import rerank
from app.utils.web_search import search_web_results
from app.vectorstore.chroma_client import get_or_create_collection


class RetrievalState(TypedDict, total=False):
    """检索图的共享状态。recall/web 写各自的 key，merge 读两者产出 sources。"""
    question: str
    kb_id: str
    top_k: int
    enable_web_search: bool
    local_docs: list[SourceDocument]
    web_docs: list[SourceDocument]
    sources: list[SourceDocument]


async def _recall_node(state: RetrievalState) -> dict:
    """本地向量召回：rerank 启用时召回 recall_k 个候选，否则召回 top_k 个。"""
    top_k = state["top_k"]
    recall_k = settings.rerank_recall_k if settings.rerank_enabled else top_k

    embeddings = get_embeddings()
    query_vector = await embeddings.aembed_query(state["question"])
    collection = get_or_create_collection(state["kb_id"])
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
    return {"local_docs": sources}


async def _web_node(state: RetrievalState) -> dict:
    """联网搜索：未开启时短路返回空；search_web_results 自带硬超时 + 失败降级。"""
    if not state.get("enable_web_search"):
        return {"web_docs": []}
    docs = await search_web_results(state["question"], top_k=state["top_k"])
    return {"web_docs": docs}


async def _merge_node(state: RetrievalState) -> dict:
    """多路融合：合并本地 + 网页候选，rerank 精排取 top_k（rerank 关闭时按顺序截断）。"""
    top_k = state["top_k"]
    candidates = list(state.get("local_docs") or []) + list(state.get("web_docs") or [])
    if not candidates:
        return {"sources": []}
    if not settings.rerank_enabled:
        return {"sources": candidates[:top_k]}

    order = await rerank(state["question"], [s.content for s in candidates], top_k)
    reranked: list[SourceDocument] = []
    for rank, idx in enumerate(order):
        s = candidates[idx]
        s.rerank_score = round(1.0 / (rank + 1), 4)  # 展示用相对精排分（降序）
        reranked.append(s)
    return {"sources": reranked}


def _build_graph():
    """组装并编译检索图（模块加载时构建一次，全局复用）。"""
    g = StateGraph(RetrievalState)
    g.add_node("recall", _recall_node)
    g.add_node("web", _web_node)
    g.add_node("merge", _merge_node)
    # recall 与 web 并行 fan-out，均完成后 fan-in 到 merge
    g.add_edge(START, "recall")
    g.add_edge(START, "web")
    g.add_edge("recall", "merge")
    g.add_edge("web", "merge")
    g.add_edge("merge", END)
    return g.compile()


# 编译后的检索图单例
_RETRIEVAL_GRAPH = _build_graph()


async def retrieve(req: RagChatRequest) -> list[SourceDocument]:
    """对外入口：运行检索图，返回最终 top_k 条来源（含本地 + 可选联网，已 rerank）。"""
    state = await _RETRIEVAL_GRAPH.ainvoke({
        "question": req.question,
        "kb_id": req.kb_id,
        "top_k": req.top_k,
        "enable_web_search": getattr(req, "enable_web_search", False),
    })
    return state.get("sources", [])
