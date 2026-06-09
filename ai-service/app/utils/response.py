"""统一的 JSON 响应包装，与 Spring Boot 的 Result<T> 结构对齐（code/message/data）。

FastAPI 内部接口均通过此包装返回，Spring Boot 的 FastApiClient 按照同一结构解包。
非流式端点（Agent / Workflow / Embedding）使用此包装；流式端点（Chat / RAG）直接返回
StreamingResponse（SSE），不经过此包装。
"""
from typing import Any, Optional
from pydantic import BaseModel


class Result(BaseModel):
    """
    标准 JSON 响应结构。

    与 Spring Boot workspace-common 模块的 ``Result<T>`` 保持字段名一致，
    便于 FastApiClient 统一解包：code=200 表示成功，否则为业务错误。
    """
    code: int = 200
    message: str = "success"
    data: Optional[Any] = None

    @classmethod
    def ok(cls, data: Any = None, message: str = "success") -> "Result":
        """构造成功响应（code=200）。"""
        return cls(code=200, message=message, data=data)
