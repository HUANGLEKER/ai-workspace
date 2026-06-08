"""对话相关的请求/响应数据模型。"""
from pydantic import BaseModel
from typing import Optional


class Message(BaseModel):
    """单条对话消息。"""
    role: str  # 角色：user | assistant | system
    content: str


class ChatRequest(BaseModel):
    """对话请求体。"""
    session_id: str            # 会话 ID
    messages: list[Message]    # 历史 + 当前消息列表
    model: Optional[str] = None  # 指定模型，缺省则用服务默认模型
    stream: bool = True
    temperature: float = 0.7
    max_tokens: Optional[int] = None
