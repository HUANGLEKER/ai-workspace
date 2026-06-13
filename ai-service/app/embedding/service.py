"""文档嵌入管道。

从 MinIO 下载文档原文 → 切片 → 调用嵌入模型生成向量 → 写入 ChromaDB。
临时文件写入系统临时目录（tempfile.gettempdir()），跨平台可用（含 Windows 原生）。
嵌入与写入按 EMBED_BATCH_SIZE 分批流式处理，避免大文档把全部向量堆在内存导致 OOM。
"""
import os
import tempfile
import uuid
from typing import Any
from langchain_text_splitters import RecursiveCharacterTextSplitter
from langchain_community.document_loaders import PyPDFLoader, Docx2txtLoader, TextLoader
from langchain_core.document_loaders import BaseLoader
from minio import Minio
from app.config.settings import settings
from app.llm.provider import get_embeddings
from app.models.embedding import EmbeddingBuildRequest, EmbeddingBuildResponse
from app.vectorstore.chroma_client import get_or_create_collection, delete_by_document


# 每批嵌入 / 写入的切片数：在内存占用与请求往返之间取平衡，避免大文档一次性堆全部向量。
EMBED_BATCH_SIZE = 64


def _get_minio_client() -> Minio:
    """创建 MinIO 客户端。"""
    return Minio(
        settings.minio_endpoint,
        access_key=settings.minio_access_key,
        secret_key=settings.minio_secret_key,
        secure=settings.minio_secure,
    )


def _load_document(file_path: str, file_name: str) -> list[str]:
    """从 MinIO 下载文档并按扩展名选择加载器解析为纯文本段落列表。

    流程：拉取对象 → 写入临时文件 → 按 pdf/docx/纯文本选择 Loader 解析 → 清理临时文件。
    """
    client = _get_minio_client()
    response = client.get_object(settings.minio_bucket, file_path)
    try:
        raw = response.read()
    finally:
        # 务必释放底层连接，避免连接泄漏
        response.close()
        response.release_conn()

    # 用随机文件名落临时盘，避免并发冲突
    suffix = file_name.rsplit(".", 1)[-1].lower()
    tmp_dir = tempfile.gettempdir()
    tmp_path = os.path.join(tmp_dir, f"{uuid.uuid4()}.{suffix}")

    with open(tmp_path, "wb") as f:
        f.write(raw)

    try:
        # 按文件类型选择对应的 LangChain 文档加载器
        loader: BaseLoader
        if suffix == "pdf":
            loader = PyPDFLoader(tmp_path)
        elif suffix in ("docx", "doc"):
            loader = Docx2txtLoader(tmp_path)
        else:
            loader = TextLoader(tmp_path, encoding="utf-8")

        docs = loader.load()
        return [d.page_content for d in docs]
    finally:
        # 无论解析成败都删除临时文件
        if os.path.exists(tmp_path):
            os.remove(tmp_path)


async def build_embedding(req: EmbeddingBuildRequest) -> EmbeddingBuildResponse:
    """为单个文档构建嵌入索引并写入对应知识库集合。"""
    # 先删除该文档的旧向量，保证重建幂等
    delete_by_document(req.kb_id, req.document_id)

    # 加载原文并按指定切片大小/重叠切分
    texts = _load_document(req.file_path, req.file_name)
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=req.chunk_size,
        chunk_overlap=req.chunk_overlap,
    )
    chunks = splitter.create_documents(texts)

    embeddings = get_embeddings()
    collection = get_or_create_collection(req.kb_id)

    # 分批嵌入 + 分批写入：大文档逐批生成向量并立即 upsert，处理完即释放，
    # 避免把全部切片的向量同时驻留内存导致 OOM。aembed_documents 还能让兼容
    # 提供商一次请求嵌入整批，较逐条 aembed_query 显著降低往返开销。
    total = 0
    for start in range(0, len(chunks), EMBED_BATCH_SIZE):
        batch = chunks[start:start + EMBED_BATCH_SIZE]
        texts_batch = [c.page_content for c in batch]
        vectors = await embeddings.aembed_documents(texts_batch)
        ids = [f"{req.document_id}_{start + j}" for j in range(len(batch))]  # 切片 ID = 文档ID_全局序号
        metas: list[dict[str, Any]] = [
            {"document_id": req.document_id, "file_name": req.file_name, "chunk_index": start + j}
            for j in range(len(batch))
        ]
        collection.upsert(ids=ids, documents=texts_batch, embeddings=vectors, metadatas=metas)  # type: ignore
        total += len(batch)

    return EmbeddingBuildResponse(
        document_id=req.document_id,
        chunk_count=total,
        status="success",
    )
