"""全局配置。

通过 Pydantic BaseSettings 从 `.env` 文件加载（字段名大写后即对应环境变量），
覆盖 LLM、嵌入、Redis、ChromaDB、MinIO 与服务自身等所有可配置项。
"""
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",  # 忽略 .env 中未声明的额外变量
    )

    # LLM（对话）配置
    llm_provider: str = "openai"
    llm_api_key: str = "sk-xxx"
    llm_api_base: str = "https://api.openai.com/v1"
    llm_model: str = "gpt-4o-mini"
    # LLM/嵌入调用的单次请求超时（秒）与自动重试次数——
    # 用于限制上游卡死时对 chat/RAG 流的阻塞时长。
    llm_timeout: float = 60.0
    llm_max_retries: int = 2

    # 嵌入配置——可指向与对话不同的提供商（如硅基流动 + bge-m3），
    # 而对话仍使用 DeepSeek。未设置时回退到 LLM_* 配置。
    embedding_api_key: str = ""
    embedding_api_base: str = ""
    embedding_model: str = "text-embedding-3-small"

    @property
    def resolved_embedding_api_key(self) -> str:
        """嵌入 API Key：未单独配置时回退到对话 LLM 的 Key。"""
        return self.embedding_api_key or self.llm_api_key

    @property
    def resolved_embedding_api_base(self) -> str:
        """嵌入 API Base：未单独配置时回退到对话 LLM 的 Base。"""
        return self.embedding_api_base or self.llm_api_base

    # Redis（AI 服务使用 DB 1，Spring Boot 使用 DB 0）
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_password: str = ""
    redis_db: int = 1

    # ChromaDB 向量库；集合名以 prefix + kb_id 拼接
    chroma_host: str = "localhost"
    chroma_port: int = 8000
    chroma_collection_prefix: str = "ai_workspace"

    # MinIO 对象存储（文档原文来源）
    minio_endpoint: str = "localhost:9000"
    minio_access_key: str = "minioadmin"
    minio_secret_key: str = "minioadmin"
    minio_bucket: str = "ai-workspace"
    minio_secure: bool = False

    # 服务自身
    app_host: str = "0.0.0.0"
    app_port: int = 8001
    app_debug: bool = True

    # CORS——逗号分隔的允许来源。本服务通常由 Spring Boot 服务端对服务端调用，
    # 故 "*"（不带凭证）是安全默认值；若直接暴露给浏览器请收紧为显式来源。
    cors_origins: str = "*"

    @property
    def cors_origin_list(self) -> list[str]:
        """将逗号分隔的 cors_origins 解析为去空后的列表。"""
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]


# 全局单例配置，供各模块直接导入使用
settings = Settings()
