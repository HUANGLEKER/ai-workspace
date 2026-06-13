"""联网搜索工具层。

provider 可插拔（当前内置 duckduckgo，预留 tavily），统一收口三项防御：
  1. 硬超时：单次搜索经 asyncio.wait_for(settings.web_search_timeout) 包裹，
     上游卡死时立即放弃，避免拖垮 FastAPI 协程乃至上游 Gin 连接池。
  2. 正文截断：每条结果正文截断至 settings.web_search_max_content 字符，
     防止长网页进入 rerank / LLM 上下文导致 token 溢出。
  3. 失败降级：任何异常（超时 / 限流 / provider 缺失）一律吞掉返回空列表，
     联网搜索永不阻断 Chat / RAG 主流程（fail-open）。

对外暴露：
  - search_web_results(query, top_k) -> list[SourceDocument]：结构化结果，供 RAG 多路召回 / workflow search 节点。
  - web_search：LangChain @tool 封装，供 Chat 侧 LLM tool-calling 使用。
"""
import asyncio
import logging

from langchain_core.tools import tool

from app.config.settings import settings
from app.models.rag import SourceDocument

logger = logging.getLogger("ai-service")


def _truncate(text: str) -> str:
    """正文按配置上限截断，超长时追加省略标记。"""
    limit = settings.web_search_max_content
    text = text or ""
    return text if len(text) <= limit else text[:limit] + "…（已截断）"


async def _duckduckgo(query: str, top_k: int) -> list[SourceDocument]:
    """DuckDuckGo provider：免费、无需 key，仅返回 SERP 摘要（title/href/body）。"""
    from duckduckgo_search import AsyncDDGS

    results = await AsyncDDGS().text(query, max_results=top_k)
    sources: list[SourceDocument] = []
    for i, res in enumerate(results or []):
        href = res.get("href", "")
        body = _truncate(res.get("body", ""))
        sources.append(
            SourceDocument(
                document_id=f"web_{i}",
                file_name=href or res.get("title", "网页"),
                # 正文同时带上标题，提升 rerank/LLM 的可读性；href 已存于 file_name
                content=f"{res.get('title', '')}\n{body}".strip(),
                # DDG 不给相关度分，用 SERP 名次的倒数近似（降序），由 rerank 兜底真实排序
                score=round(1.0 / (i + 1), 4),
                source_type="web",
            )
        )
    return sources


async def _tavily(query: str, top_k: int) -> list[SourceDocument]:
    """Tavily provider（预留）：为 LLM/RAG 设计，返回清洗正文 + 真实相关度分。

    需安装 tavily-python 并配置 web_search_api_key。当前未启用——切换时取消下方导入注释即可。
    """
    raise NotImplementedError("tavily provider 尚未启用：请 uv add tavily-python 并配置 WEB_SEARCH_API_KEY")


# provider 注册表：新增 provider 在此登记，search_web_results 据 settings.web_search_provider 分发
_PROVIDERS = {
    "duckduckgo": _duckduckgo,
    "tavily": _tavily,
}


async def search_web_results(query: str, top_k: int = 3) -> list[SourceDocument]:
    """执行联网搜索，返回标准化 SourceDocument 列表（source_type="web"）。

    硬超时 + 全异常降级：任何失败都返回空列表，绝不向上抛出以阻断主流程。
    """
    if not query or not query.strip():
        return []
    top_k = max(1, min(top_k, settings.web_search_max_results))
    provider = _PROVIDERS.get(settings.web_search_provider, _duckduckgo)
    try:
        return await asyncio.wait_for(provider(query, top_k), timeout=settings.web_search_timeout)
    except asyncio.TimeoutError:
        logger.warning('{"event":"web_search_timeout","query":%r,"timeout":%s}', query[:120], settings.web_search_timeout)
        return []
    except Exception as e:  # noqa: BLE001 — 联网搜索失败不应阻断 Chat/RAG，降级返回空结果
        logger.warning('{"event":"web_search_failed","error":%r}', str(e)[:200])
        return []


@tool
async def web_search(query: str) -> str:
    """当用户询问实时信息、最新新闻或需要联网查询时使用此工具。
    返回搜索到的网页片段内容，你需要根据这些片段回答用户。"""
    sources = await search_web_results(query, top_k=3)
    if not sources:
        return "未能搜索到相关结果。"
    context = "\n\n---\n\n".join(
        f"[来源{i + 1}] ({s.file_name})\n{s.content}" for i, s in enumerate(sources)
    )
    return f"以下是联网搜索结果：\n\n{context}"
