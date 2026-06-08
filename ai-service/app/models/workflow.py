"""工作流运行相关的数据模型。"""
from pydantic import BaseModel
from typing import Any, Optional


class WorkflowRunRequest(BaseModel):
    """工作流运行请求体。"""
    workflow_id: str
    session_id: str
    inputs: dict[str, Any] = {}  # 工作流输入参数
    model: Optional[str] = None


class WorkflowRunResponse(BaseModel):
    """工作流运行响应体。"""
    session_id: str
    workflow_id: str
    outputs: dict[str, Any] = {}  # 工作流输出结果
    status: str  # completed | failed
