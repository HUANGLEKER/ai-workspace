"""LLM 提供方抽象。

通过 LangChain 封装 OpenAI 兼容的对话模型与嵌入模型。客户端实例使用 lru_cache
缓存，避免每次请求重复创建连接；对话与嵌入可指向不同提供商（见 settings）。
"""
from functools import lru_cache
from pydantic import SecretStr
from langchain_openai import ChatOpenAI, OpenAIEmbeddings
from app.config.settings import settings


@lru_cache(maxsize=16)
def _build_chat_llm(model: str, api_key: str, base_url: str, temperature: float) -> ChatOpenAI:
    """按 (model, key, base, temperature) 缓存构建客户端。

    缓存 key 含 api_key 明文，仅驻留进程内存，不落日志；lru_cache 上限防止
    key 轮换场景下客户端实例无限堆积。
    """
    return ChatOpenAI(
        model=model,
        temperature=temperature,
        api_key=SecretStr(api_key),
        base_url=base_url,
        streaming=True,
        timeout=settings.llm_timeout,
        max_retries=settings.llm_max_retries,
    )


def get_chat_llm(model: str | None = None, temperature: float = 0.7,
                 api_key: str | None = None, api_base: str | None = None) -> ChatOpenAI:
    """获取对话 LLM 客户端。

    api_key/api_base 由请求级 llm_config 提供（多模型路由）；任一为空则该项
    回退 .env 全局配置，完全向后兼容旧调用方。
    """
    return _build_chat_llm(
        model=model or settings.llm_model,
        api_key=api_key or settings.llm_api_key,
        base_url=api_base or settings.llm_api_base,
        temperature=temperature,
    )


@lru_cache(maxsize=2)
def get_embeddings() -> OpenAIEmbeddings:
    """获取嵌入模型客户端（缓存）；使用 resolved_* 配置以支持独立的嵌入提供商。"""
    return OpenAIEmbeddings(
        model=settings.embedding_model,
        api_key=SecretStr(settings.resolved_embedding_api_key),
        base_url=settings.resolved_embedding_api_base,
        timeout=settings.llm_timeout,
        max_retries=settings.llm_max_retries,
    )
