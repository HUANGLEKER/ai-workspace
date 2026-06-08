"""统一的 JSON 响应包装，与 Spring Boot 的 Result 结构对齐（code/message/data）。"""
from typing import Any, Optional
from pydantic import BaseModel


class Result(BaseModel):
    """标准响应包装。"""
    code: int = 200
    message: str = "success"
    data: Optional[Any] = None

    @classmethod
    def ok(cls, data: Any = None, message: str = "success") -> "Result":
        """构造一个成功响应（code=200）。"""
        return cls(code=200, message=message, data=data)
