"""Agent 运行相关的数据模型。"""
from pydantic import BaseModel
from typing import Any, Optional


class HttpToolSpec(BaseModel):
    """Agent 可调用的 HTTP 工具规格，由 Spring Boot 从工具中心解析得到。"""
    name: str
    description: str = ""
    endpoint: str
    # config 结构：{"method": "GET|POST", "params": [{"name","type","description","required"}], "headers": {...}}
    config: dict[str, Any] = {}


class McpServerSpec(BaseModel):
    """运行时加载其工具的 SSE MCP 服务器规格。"""
    name: str
    url: str
    transport: str = "sse"
    headers: dict[str, str] = {}


class AgentRunRequest(BaseModel):
    """Agent 运行请求体。"""
    session_id: str
    input: str                              # 用户输入
    tools: list[HttpToolSpec] = []          # 可用的 HTTP 工具
    mcp_servers: list[McpServerSpec] = []   # 可用的 MCP 服务器
    system_prompt: Optional[str] = None     # 系统提示
    model: Optional[str] = None


class AgentRunResponse(BaseModel):
    """Agent 运行响应体。"""
    session_id: str
    output: str                             # 最终答案
    steps: list[dict[str, Any]] = []        # 执行轨迹（思考/工具调用/结果）
