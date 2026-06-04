from pydantic import BaseModel
from typing import Any, Optional


class WorkflowRunRequest(BaseModel):
    workflow_id: str
    session_id: str
    inputs: dict[str, Any] = {}
    model: Optional[str] = None


class WorkflowRunResponse(BaseModel):
    session_id: str
    workflow_id: str
    outputs: dict[str, Any] = {}
    status: str  # completed | failed
