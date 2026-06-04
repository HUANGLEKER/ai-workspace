from functools import lru_cache
import chromadb
from chromadb import Collection
from app.config.settings import settings


@lru_cache(maxsize=1)
def get_chroma_client() -> chromadb.HttpClient:
    return chromadb.HttpClient(
        host=settings.chroma_host,
        port=settings.chroma_port,
    )


def _collection_name(kb_id: str) -> str:
    return f"{settings.chroma_collection_prefix}_{kb_id}"


def get_or_create_collection(kb_id: str) -> Collection:
    client = get_chroma_client()
    return client.get_or_create_collection(
        name=_collection_name(kb_id),
        metadata={"hnsw:space": "cosine"},
    )


def delete_collection(kb_id: str) -> None:
    client = get_chroma_client()
    try:
        client.delete_collection(_collection_name(kb_id))
    except Exception:
        pass


def delete_by_document(kb_id: str, document_id: str) -> None:
    collection = get_or_create_collection(kb_id)
    collection.delete(where={"document_id": document_id})
