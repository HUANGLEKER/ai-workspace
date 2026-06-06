from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # LLM
    llm_provider: str = "openai"
    llm_api_key: str = "sk-xxx"
    llm_api_base: str = "https://api.openai.com/v1"
    llm_model: str = "gpt-4o-mini"
    llm_embedding_model: str = "text-embedding-3-small"
    # Per-request timeout (seconds) and automatic retry count for LLM/embedding
    # calls — bounds how long a hung upstream can stall a chat/RAG stream.
    llm_timeout: float = 60.0
    llm_max_retries: int = 2

    # Redis
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_password: str = ""
    redis_db: int = 1

    # ChromaDB
    chroma_host: str = "localhost"
    chroma_port: int = 8000
    chroma_collection_prefix: str = "ai_workspace"

    # MinIO
    minio_endpoint: str = "localhost:9000"
    minio_access_key: str = "minioadmin"
    minio_secret_key: str = "minioadmin"
    minio_bucket: str = "ai-workspace"
    minio_secure: bool = False

    # Service
    app_host: str = "0.0.0.0"
    app_port: int = 8001
    app_debug: bool = True

    # CORS — comma-separated list of allowed origins. The service is normally
    # called server-to-server by Spring Boot, so "*" (without credentials) is a
    # safe default; tighten to explicit origins if exposed to browsers directly.
    cors_origins: str = "*"

    @property
    def cors_origin_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]


settings = Settings()
