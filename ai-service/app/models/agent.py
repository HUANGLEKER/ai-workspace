from pydantic import BaseModel
from typing import Any, Optional


class HttpToolSpec(BaseModel):
    """An HTTP tool the agent may call. Resolved by Spring Boot from the Tool Center."""
    name: str
    description: str = ""
    endpoint: str
    # {"method": "GET|POST", "params": [{"name","type","description","required"}], "headers": {...}}
    config: dict[str, Any] = {}


class McpServerSpec(BaseModel):
    """An SSE MCP server whose tools are loaded at runtime."""
    name: str
    url: str
    transport: str = "sse"
    headers: dict[str, str] = {}


class AgentRunRequest(BaseModel):
    session_id: str
    input: str
    tools: list[HttpToolSpec] = []
    mcp_servers: list[McpServerSpec] = []
    system_prompt: Optional[str] = None
    model: Optional[str] = None


class AgentRunResponse(BaseModel):
    session_id: str
    output: str
    steps: list[dict[str, Any]] = []
