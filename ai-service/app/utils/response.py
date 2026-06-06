from typing import Any, Optional
from pydantic import BaseModel


class Result(BaseModel):
    code: int = 200
    message: str = "success"
    data: Optional[Any] = None

    @classmethod
    def ok(cls, data: Any = None, message: str = "success") -> "Result":
        return cls(code=200, message=message, data=data)
