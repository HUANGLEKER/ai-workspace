from pydantic import BaseModel
from typing import Optional


class RagChatRequest(BaseModel):
    session_id: str
    kb_id: str
    question: str
    top_k: int = 4
    stream: bool = True
    temperature: float = 0.3
    model: Optional[str] = None


class SourceDocument(BaseModel):
    document_id: str
    file_name: str
    content: str
    score: float


class RagChatResponse(BaseModel):
    session_id: str
    answer: str
    sources: list[SourceDocument] = []
