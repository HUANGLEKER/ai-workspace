"""文档嵌入相关的数据模型。"""
from pydantic import BaseModel
from typing import Optional


class EmbeddingBuildRequest(BaseModel):
    """构建嵌入索引的请求体。"""
    kb_id: str
    document_id: str
    file_path: str           # MinIO 对象路径
    file_name: str
    chunk_size: int = 500    # 切片大小
    chunk_overlap: int = 50  # 切片重叠长度


class EmbeddingBuildResponse(BaseModel):
    """构建嵌入索引的响应体。"""
    document_id: str
    chunk_count: int         # 生成的切片数量
    status: str              # success | failed


class EmbeddingDeleteRequest(BaseModel):
    """删除向量的请求体。"""
    kb_id: str
    document_id: Optional[str] = None  # 为 None 表示删除整个知识库集合
