"""RAG 知识库问答相关的数据模型。"""
from pydantic import BaseModel
from typing import Optional


class RagChatRequest(BaseModel):
    """RAG 问答请求体。"""
    session_id: str
    kb_id: str          # 目标知识库 ID
    question: str       # 用户问题
    top_k: int = 4      # 检索返回的相关切片数量
    stream: bool = True
    temperature: float = 0.3  # 问答温度偏低以提升答案稳定性
    model: Optional[str] = None


class SourceDocument(BaseModel):
    """命中的来源切片，用于前端展示引用。"""
    document_id: str
    file_name: str
    content: str
    score: float  # 相似度分数（1 - 余弦距离）
