from pydantic import BaseModel
from typing import Any, Optional


class AgentRunRequest(BaseModel):
    session_id: str
    input: str
    tools: list[str] = []
    model: Optional[str] = None


class AgentRunResponse(BaseModel):
    session_id: str
    output: str
    steps: list[dict[str, Any]] = []
