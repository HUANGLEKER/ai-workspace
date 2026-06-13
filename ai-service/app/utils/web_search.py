"""网络搜索工具，提供免费的 DuckDuckGo 搜索能力。"""
from duckduckgo_search import AsyncDDGS
from langchain_core.tools import tool

from app.models.rag import SourceDocument

async def search_web_results(query: str, top_k: int = 3) -> list[SourceDocument]:
    """异步执行网络搜索并返回标准化后的 SourceDocument 列表。"""
    sources = []
    try:
        # 使用 AsyncDDGS 异步请求
        results = await AsyncDDGS().text(query, max_results=top_k)
        if not results:
            return sources
            
        for i, res in enumerate(results):
            # duckduckgo_search 返回的包含 title, href, body
            title = res.get("title", "")
            href = res.get("href", "")
            body = res.get("body", "")
            
            content = f"标题: {title}\n链接: {href}\n片段: {body}"
            
            sources.append(
                SourceDocument(
                    document_id=f"web_{i}",
                    file_name=href,
                    content=content,
                    score=0.99,  # 默认高分
                    source_type="web"
                )
            )
    except Exception as e:
        # 吞掉异常，返回空列表，避免整个流程中断
        print(f"Web search error: {e}")
        pass
    
    return sources

@tool
async def web_search(query: str) -> str:
    """当用户询问实时信息、最新新闻或需要联网查询时使用此工具。
    返回搜索到的网页片段内容，你需要根据这些片段回答用户。"""
    sources = await search_web_results(query, top_k=3)
    if not sources:
        return "未能搜索到相关结果。"
    
    context = "\n\n---\n\n".join(
        f"[来源{i+1}] ({s.file_name})\n{s.content}" for i, s in enumerate(sources)
    )
    return f"以下是联网搜索结果：\n\n{context}"
