import httpx
from typing import Any, Optional
from pydantic import create_model
from langchain_core.messages import HumanMessage, SystemMessage, ToolMessage
from langchain_core.tools import StructuredTool
from app.llm.provider import get_chat_llm
from app.models.agent import AgentRunRequest, AgentRunResponse, HttpToolSpec, McpServerSpec

# how many think→act cycles before we stop, to bound tool-calling loops
MAX_ITERATIONS = 6

_TYPE_MAP: dict[str, type] = {
    "string": str,
    "number": float,
    "integer": int,
    "boolean": bool,
    "object": dict,
    "array": list,
}


def _build_http_tool(spec: HttpToolSpec) -> StructuredTool:
    """Turn a Tool Center HTTP definition into a callable LangChain tool."""
    config = spec.config or {}
    method = str(config.get("method") or "POST").upper()
    params = config.get("params") or []
    headers = config.get("headers") or {}

    # derive an args schema from the declared parameters so the LLM knows what to pass
    fields: dict[str, Any] = {}
    for p in params:
        name = p.get("name")
        if not name:
            continue
        py_type = _TYPE_MAP.get(str(p.get("type", "string")).lower(), str)
        if p.get("required", False):
            fields[name] = (py_type, ...)
        else:
            fields[name] = (Optional[py_type], None)
    args_model = create_model(f"{spec.name}_Args", **fields)

    async def _call(**kwargs: Any) -> str:
        # drop unset optional args so they don't leak into the request
        payload = {k: v for k, v in kwargs.items() if v is not None}
        async with httpx.AsyncClient(timeout=30.0) as client:
            if method == "GET":
                resp = await client.get(spec.endpoint, params=payload, headers=headers)
            else:
                resp = await client.request(method, spec.endpoint, json=payload, headers=headers)
            resp.raise_for_status()
            return resp.text[:4000]

    return StructuredTool.from_function(
        coroutine=_call,
        name=spec.name,
        description=spec.description or spec.name,
        args_schema=args_model,
    )


async def _load_mcp_tools(servers: list[McpServerSpec], steps: list[dict]) -> list:
    """Load tools exposed by SSE MCP servers via langchain-mcp-adapters."""
    if not servers:
        return []
    try:
        from langchain_mcp_adapters.client import MultiServerMCPClient
    except ImportError:
        steps.append({"type": "warning", "content": "langchain-mcp-adapters 未安装，已跳过 MCP 工具"})
        return []

    connections: dict[str, dict] = {}
    for s in servers:
        if s.transport == "sse" and s.url:
            conn: dict[str, Any] = {"url": s.url, "transport": "sse"}
            if s.headers:
                conn["headers"] = s.headers
            connections[s.name] = conn
    if not connections:
        return []

    try:
        client = MultiServerMCPClient(connections)
        tools = await client.get_tools()
        steps.append({"type": "mcp", "content": f"已从 {len(connections)} 个MCP服务器加载 {len(tools)} 个工具"})
        return tools
    except Exception as e:  # noqa: BLE001 — a bad MCP server must not abort the run
        steps.append({"type": "warning", "content": f"MCP工具加载失败: {e}"})
        return []


async def run_agent(req: AgentRunRequest) -> AgentRunResponse:
    steps: list[dict] = []

    tools = [_build_http_tool(t) for t in req.tools]
    tools += await _load_mcp_tools(req.mcp_servers, steps)
    tool_map = {t.name: t for t in tools}

    llm = get_chat_llm(model=req.model)
    runnable = llm.bind_tools(tools) if tools else llm

    messages: list = []
    if req.system_prompt:
        messages.append(SystemMessage(content=req.system_prompt))
    messages.append(HumanMessage(content=req.input))

    output = ""
    for _ in range(MAX_ITERATIONS):
        ai = await runnable.ainvoke(messages)
        messages.append(ai)
        if ai.content:
            output = ai.content if isinstance(ai.content, str) else str(ai.content)
            steps.append({"type": "llm", "content": output})

        tool_calls = getattr(ai, "tool_calls", None) or []
        if not tool_calls:
            break

        for tc in tool_calls:
            name = tc.get("name")
            args = tc.get("args", {})
            steps.append({"type": "tool_call", "tool": name, "args": args})
            tool = tool_map.get(name)
            if tool is None:
                result = f"未找到工具: {name}"
            else:
                try:
                    result = await tool.ainvoke(args)
                except Exception as e:  # noqa: BLE001 — surface tool errors back to the model
                    result = f"工具执行出错: {e}"
            result_str = str(result)
            steps.append({"type": "tool_result", "tool": name, "content": result_str[:2000]})
            messages.append(ToolMessage(content=result_str, tool_call_id=tc.get("id", name)))

    return AgentRunResponse(session_id=req.session_id, output=output, steps=steps)
