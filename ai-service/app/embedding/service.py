import io
import uuid
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain_community.document_loaders import PyPDFLoader, Docx2txtLoader, TextLoader
from minio import Minio
from app.config.settings import settings
from app.llm.provider import get_embeddings
from app.models.embedding import EmbeddingBuildRequest, EmbeddingBuildResponse
from app.vectorstore.chroma_client import get_or_create_collection, delete_by_document


def _get_minio_client() -> Minio:
    return Minio(
        settings.minio_endpoint,
        access_key=settings.minio_access_key,
        secret_key=settings.minio_secret_key,
        secure=settings.minio_secure,
    )


def _load_document(file_path: str, file_name: str) -> list[str]:
    client = _get_minio_client()
    response = client.get_object(settings.minio_bucket, file_path)
    raw = response.read()
    response.close()

    suffix = file_name.rsplit(".", 1)[-1].lower()
    tmp_path = f"/tmp/{uuid.uuid4()}.{suffix}"
    with open(tmp_path, "wb") as f:
        f.write(raw)

    if suffix == "pdf":
        loader = PyPDFLoader(tmp_path)
    elif suffix in ("docx", "doc"):
        loader = Docx2txtLoader(tmp_path)
    else:
        loader = TextLoader(tmp_path, encoding="utf-8")

    docs = loader.load()
    return [d.page_content for d in docs]


async def build_embedding(req: EmbeddingBuildRequest) -> EmbeddingBuildResponse:
    delete_by_document(req.kb_id, req.document_id)

    texts = _load_document(req.file_path, req.file_name)
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=req.chunk_size,
        chunk_overlap=req.chunk_overlap,
    )
    chunks = splitter.create_documents(texts)

    embeddings = get_embeddings()
    collection = get_or_create_collection(req.kb_id)

    ids, docs, embeds, metas = [], [], [], []
    for i, chunk in enumerate(chunks):
        chunk_id = f"{req.document_id}_{i}"
        vector = await embeddings.aembed_query(chunk.page_content)
        ids.append(chunk_id)
        docs.append(chunk.page_content)
        embeds.append(vector)
        metas.append({"document_id": req.document_id, "file_name": req.file_name, "chunk_index": i})

    if ids:
        collection.upsert(ids=ids, documents=docs, embeddings=embeds, metadatas=metas)

    return EmbeddingBuildResponse(
        document_id=req.document_id,
        chunk_count=len(ids),
        status="success",
    )
