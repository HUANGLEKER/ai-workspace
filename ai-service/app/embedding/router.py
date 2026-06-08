"""嵌入路由：构建文档索引与删除向量（按文档或整库）。"""
from fastapi import APIRouter
from app.models.embedding import EmbeddingBuildRequest, EmbeddingDeleteRequest
from app.embedding.service import build_embedding
from app.vectorstore.chroma_client import delete_by_document, delete_collection
from app.utils.response import Result

router = APIRouter(prefix="/embedding", tags=["Embedding"])


@router.post("/build", response_model=Result)
async def build(req: EmbeddingBuildRequest):
    """构建单个文档的嵌入索引。"""
    result = await build_embedding(req)
    return Result.ok(data=result.model_dump())


@router.delete("/delete", response_model=Result)
async def delete(req: EmbeddingDeleteRequest):
    """删除向量：指定 document_id 则删单个文档，否则删整个知识库集合。"""
    if req.document_id:
        delete_by_document(req.kb_id, req.document_id)
    else:
        delete_collection(req.kb_id)
    return Result.ok()
