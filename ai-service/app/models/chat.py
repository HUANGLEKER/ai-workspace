"""对话相关的请求/响应数据模型。"""
from pydantic import BaseModel
from typing import Optional


class Message(BaseModel):
    """单条对话消息。"""
    role: str  # 角色：user | assistant | system
    content: str


class LlmConfig(BaseModel):
    """按请求覆盖的 LLM 提供方配置（多模型路由）。

    由 Go 后端从 chat_model 表读出后随请求透传，仅在服务间内网流转，
    不对客户端暴露。字段为空字符串视为未提供，回退 .env 全局配置。
    注意：不能命名为 model_config——那是 Pydantic v2 的保留属性。
    """
    api_base: str = ""
    api_key: str = ""


class ChatRequest(BaseModel):
    """对话请求体。"""
    session_id: str            # 会话 ID
    messages: list[Message]    # 历史 + 当前消息列表
    model: Optional[str] = None  # 指定模型，缺省则用服务默认模型
    llm_config: Optional[LlmConfig] = None  # 按请求覆盖提供方（多模型路由）
    stream: bool = True
    temperature: float = 0.7
    max_tokens: Optional[int] = None


class SummarizeRequest(BaseModel):
    """会话滚动摘要请求体（P3-2 Memory 层）。"""
    session_id: str
    summary: str = ""          # 已有摘要（增量更新的基础）
    messages: list[Message]    # 本批待压缩的旧消息
    model: Optional[str] = None
    llm_config: Optional[LlmConfig] = None


class SummarizeResponse(BaseModel):
    """会话摘要响应体。"""
    summary: str
