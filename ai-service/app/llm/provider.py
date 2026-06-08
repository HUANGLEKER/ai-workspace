"""LLM 提供方抽象。

通过 LangChain 封装 OpenAI 兼容的对话模型与嵌入模型。客户端实例使用 lru_cache
缓存，避免每次请求重复创建连接；对话与嵌入可指向不同提供商（见 settings）。
"""
from functools import lru_cache
from pydantic import SecretStr
from langchain_openai import ChatOpenAI, OpenAIEmbeddings
from app.config.settings import settings


@lru_cache(maxsize=8)
def get_chat_llm(model: str | None = None, temperature: float = 0.7) -> ChatOpenAI:
    """获取对话 LLM 客户端（按 model+temperature 缓存）；model 为空时回退默认模型。"""
    return ChatOpenAI(
        model=model or settings.llm_model,
        temperature=temperature,
        api_key=SecretStr(settings.llm_api_key),
        base_url=settings.llm_api_base,
        streaming=True,
        timeout=settings.llm_timeout,
        max_retries=settings.llm_max_retries,
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
