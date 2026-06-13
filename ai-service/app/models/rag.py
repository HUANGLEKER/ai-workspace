"""RAG 知识库问答相关的数据模型。"""
from pydantic import BaseModel
from typing import Optional

from app.models.chat import LlmConfig


class RagChatRequest(BaseModel):
    """RAG 问答请求体。"""
    session_id: str
    kb_id: str          # 目标知识库 ID
    question: str       # 用户问题
    top_k: int = 4      # 检索返回的相关切片数量
    stream: bool = True
    temperature: float = 0.3  # 问答温度偏低以提升答案稳定性
    model: Optional[str] = None
    llm_config: Optional[LlmConfig] = None  # 按请求覆盖提供方（多模型路由）
    enable_web_search: bool = False



class SourceDocument(BaseModel):
    """命中的来源切片，用于前端展示引用。"""
    document_id: str
    file_name: str
    content: str
    score: float  # 向量相似度分数（1 - 余弦距离）
    rerank_score: Optional[float] = None  # rerank 精排分数（启用 rerank 时填充）
    cited: bool = False  # 是否被答案实际引用（前端高亮命中来源）
    source_type: str = "local"
