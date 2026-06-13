"""Rerank（重排）：召回后用 reranker 精排，提升 RAG 命中率（P2-3）。

调用 OpenAI 兼容的 /rerank 端点（如硅基流动 BAAI/bge-reranker-v2-m3），
按 query 对候选文档打分后取 top_n。配置缺失或调用失败时 fail-open——
返回原始顺序的前 top_n，保证 RAG 不因 rerank 故障而失败。
"""
import logging

import httpx

from app.config.settings import settings

logger = logging.getLogger("ai-service")


async def rerank(query: str, documents: list[str], top_n: int) -> list[int]:
    """对 documents 按与 query 的相关性精排，返回排序后的原始下标列表（长度 <= top_n）。

    未启用 rerank 或调用失败时，回退为原始顺序的前 top_n 下标。
    """
    n = len(documents)
    if n == 0:
        return []
    fallback = list(range(min(top_n, n)))

    if not settings.rerank_enabled:
        return fallback

    url = settings.resolved_rerank_api_base.rstrip("/") + "/rerank"
    payload = {
        "model": settings.rerank_model,
        "query": query,
        "documents": documents,
        "top_n": top_n,
    }
    headers = {"Authorization": f"Bearer {settings.resolved_rerank_api_key}"}

    try:
        async with httpx.AsyncClient(timeout=settings.llm_timeout) as client:
            resp = await client.post(url, json=payload, headers=headers)
            resp.raise_for_status()
            data = resp.json()
        # 标准响应：{"results": [{"index": i, "relevance_score": s}, ...]}（已按分数降序）
        results = data.get("results", [])
        order = [r["index"] for r in results if 0 <= r.get("index", -1) < n]
        return order[:top_n] if order else fallback
    except Exception as e:
        logger.warning('{"event":"rerank_failed","error":%r}', str(e)[:200])
        return fallback
