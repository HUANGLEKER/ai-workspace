from pydantic import BaseModel
from typing import Optional


class EmbeddingBuildRequest(BaseModel):
    kb_id: str
    document_id: str
    file_path: str           # MinIO object path
    file_name: str
    chunk_size: int = 500
    chunk_overlap: int = 50


class EmbeddingBuildResponse(BaseModel):
    document_id: str
    chunk_count: int
    status: str              # success | failed


class EmbeddingDeleteRequest(BaseModel):
    kb_id: str
    document_id: Optional[str] = None  # None means delete entire kb collection
