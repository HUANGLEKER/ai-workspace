from pydantic import BaseModel
from typing import Optional


class Message(BaseModel):
    role: str  # user | assistant | system
    content: str


class ChatRequest(BaseModel):
    session_id: str
    messages: list[Message]
    model: Optional[str] = None
    stream: bool = True
    temperature: float = 0.7
    max_tokens: Optional[int] = None


class ChatResponse(BaseModel):
    session_id: str
    content: str
    model: str
    prompt_tokens: int = 0
    completion_tokens: int = 0
