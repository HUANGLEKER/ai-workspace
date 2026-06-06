from functools import lru_cache
from pydantic import SecretStr
from langchain_openai import ChatOpenAI, OpenAIEmbeddings
from app.config.settings import settings


@lru_cache(maxsize=8)
def get_chat_llm(model: str | None = None, temperature: float = 0.7) -> ChatOpenAI:
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
    return OpenAIEmbeddings(
        model=settings.llm_embedding_model,
        api_key=SecretStr(settings.llm_api_key),
        base_url=settings.llm_api_base,
        timeout=settings.llm_timeout,
        max_retries=settings.llm_max_retries,
    )
