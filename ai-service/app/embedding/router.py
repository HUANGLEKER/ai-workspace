from fastapi import APIRouter
from app.models.embedding import EmbeddingBuildRequest, EmbeddingBuildResponse, EmbeddingDeleteRequest
from app.embedding.service import build_embedding
from app.vectorstore.chroma_client import delete_by_document, delete_collection
from app.utils.response import Result

router = APIRouter(prefix="/embedding", tags=["Embedding"])


@router.post("/build", response_model=Result)
async def build(req: EmbeddingBuildRequest):
    result = await build_embedding(req)
    return Result.ok(data=result.model_dump())


@router.delete("/delete", response_model=Result)
async def delete(req: EmbeddingDeleteRequest):
    if req.document_id:
        delete_by_document(req.kb_id, req.document_id)
    else:
        delete_collection(req.kb_id)
    return Result.ok()
