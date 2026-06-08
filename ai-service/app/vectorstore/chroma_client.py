"""ChromaDB 向量库 HTTP 客户端封装。

每个知识库（kb_id）对应一个独立集合，集合名为 `{prefix}_{kb_id}`。
提供集合的获取/创建、按文档删除与整库删除等操作。
"""
from functools import lru_cache
import chromadb
from chromadb.api import ClientAPI
from chromadb import Collection
from app.config.settings import settings


@lru_cache(maxsize=1)
def get_chroma_client() -> ClientAPI:
    """获取 ChromaDB HTTP 客户端单例。"""
    return chromadb.HttpClient(
        host=settings.chroma_host,
        port=settings.chroma_port,
    )


def _collection_name(kb_id: str) -> str:
    """由知识库 ID 拼出带前缀的集合名。"""
    return f"{settings.chroma_collection_prefix}_{kb_id}"


def get_or_create_collection(kb_id: str) -> Collection:
    """获取知识库对应集合，不存在则创建（使用余弦距离）。"""
    client = get_chroma_client()
    return client.get_or_create_collection(
        name=_collection_name(kb_id),
        metadata={"hnsw:space": "cosine"},
    )


def delete_collection(kb_id: str) -> None:
    """删除整个知识库集合；集合不存在时静默忽略。"""
    client = get_chroma_client()
    try:
        client.delete_collection(_collection_name(kb_id))
    except Exception:
        pass


def delete_by_document(kb_id: str, document_id: str) -> None:
    """按 document_id 删除某文档的所有切片向量（重建索引前先清旧数据）。"""
    collection = get_or_create_collection(kb_id)
    collection.delete(where={"document_id": document_id})
