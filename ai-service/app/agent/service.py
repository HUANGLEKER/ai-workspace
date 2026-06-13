"""工具调用 Agent 业务逻辑。

将工具中心的 HTTP 工具规格构建为可调用的 LangChain 工具，并（可选）从 SSE MCP
服务器加载工具，然后运行有界的「思考→行动」循环，返回最终答案与执行轨迹 steps。
"""
import httpx
from typing import Any, Optional
from pydantic import create_model
from langchain_core.messages import HumanMessage, SystemMessage, ToolMessage
from langchain_core.tools import StructuredTool
from app.llm.provider import get_chat_llm
from app.models.agent import AgentRunRequest, AgentRunResponse, HttpToolSpec, McpServerSpec

# 思考→行动循环的最大轮数，用于限制工具调用循环防止无限循环
MAX_ITERATIONS = 6

# 工具参数声明类型 → Python 类型的映射，用于动态构建参数 schema
_TYPE_MAP: dict[str, type] = {
    "string": str,
    "number": float,
    "integer": int,
    "boolean": bool,
    "object": dict,
    "array": list,
}


def _build_http_tool(spec: HttpToolSpec) -> StructuredTool:
    """将工具中心的 HTTP 工具定义转换为可被 LLM 调用的 LangChain 工具。"""
    config = spec.config or {}
    method = str(config.get("method") or "POST").upper()
    params = config.get("params") or []
    headers = config.get("headers") or {}

    # 依据声明的参数动态生成参数 schema，让 LLM 知道该传哪些字段
    fields: dict[str, Any] = {}
    for p in params:
        name = p.get("name")
        if not name:
            continue
        py_type = _TYPE_MAP.get(str(p.get("type", "string")).lower(), str)
        if p.get("required", False):
            fields[name] = (py_type, ...)            # 必填：无默认值
        else:
            fields[name] = (Optional[py_type], None)  # 选填：默认 None
    args_model = create_model(f"{spec.name}_Args", **fields)

    async def _call(**kwargs: Any) -> str:
        """实际执行 HTTP 调用的协程；返回响应文本（截断至 4000 字符）。"""
        # 丢弃未设置的选填参数，避免泄漏到请求中
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
    """通过 langchain-mcp-adapters 加载 SSE MCP 服务器暴露的工具。

    该库为可选依赖；缺失或服务器异常时记录到 steps 并降级返回空列表，不影响整体运行。
    """
    if not servers:
        return []
    try:
        from langchain_mcp_adapters.client import MultiServerMCPClient
    except ImportError:
        steps.append({"type": "warning", "content": "langchain-mcp-adapters 未安装，已跳过 MCP 工具"})
        return []

    # 仅处理 sse 传输且配置了 url 的服务器
    connections: dict[str, Any] = {}
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
    except Exception as e:  # noqa: BLE001 — MCP 服务器异常不应中止整次 Agent 运行，降级跳过即可
        steps.append({"type": "warning", "content": f"MCP工具加载失败: {e}"})
        return []


async def _agent_events(req: AgentRunRequest):
    """Agent 思考→行动循环的核心生成器：逐步产出 (kind, payload) 事件。

    kind="step" 表示一条执行轨迹（llm/tool_call/tool_result/mcp/warning），
    kind="answer" 表示最终答案（循环结束时产出一次）。
    run_agent（一次性）与 run_agent_stream（SSE）共用此生成器，保证两条路径行为一致。
    """
    pending: list[dict] = []  # 工具加载阶段（_load_mcp_tools）写入的 step，先缓冲再产出

    tools = [_build_http_tool(t) for t in req.tools]
    tools += await _load_mcp_tools(req.mcp_servers, pending)
    for step in pending:
        yield ("step", step)
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
            yield ("step", {"type": "llm", "content": output})

        tool_calls = getattr(ai, "tool_calls", None) or []
        if not tool_calls:
            break

        for tc in tool_calls:
            name = tc.get("name")
            args = tc.get("args", {})
            yield ("step", {"type": "tool_call", "tool": name, "args": args})
            tool = tool_map.get(name)
            if tool is None:
                result = f"未找到工具: {name}"
            else:
                try:
                    result = await tool.ainvoke(args)
                except Exception as e:  # noqa: BLE001 — 工具执行失败不应中断 Agent 循环，将错误文本回传给模型让其自行处理
                    result = f"工具执行出错: {e}"
            result_str = str(result)
            yield ("step", {"type": "tool_result", "tool": name, "content": result_str[:2000]})
            messages.append(ToolMessage(content=result_str, tool_call_id=tc.get("id", name)))

    yield ("answer", output)


async def run_agent(req: AgentRunRequest) -> AgentRunResponse:
    """运行 Agent（一次性）：消费事件生成器，聚合为答案与执行轨迹后返回。"""
    steps: list[dict] = []
    output = ""
    async for kind, payload in _agent_events(req):
        if kind == "step":
            steps.append(payload)
        else:
            output = payload
    return AgentRunResponse(session_id=req.session_id, output=output, steps=steps)


async def run_agent_stream(req: AgentRunRequest):
    """运行 Agent（流式）：将每条事件包装为 SSE 帧实时下发，[DONE] 收尾。

    帧格式：{"type":"step","session_id","step":{...}} / {"type":"answer","session_id","output":"..."}
    """
    import json

    try:
        async for kind, payload in _agent_events(req):
            if kind == "step":
                frame = {"type": "step", "session_id": req.session_id, "step": payload}
            else:
                frame = {"type": "answer", "session_id": req.session_id, "output": payload}
            yield f"data: {json.dumps(frame, ensure_ascii=False)}\n\n"
    except Exception as e:  # noqa: BLE001 — 把运行期异常作为 error 帧下发，前端可提示
        import json as _json
        err = _json.dumps({"type": "error", "session_id": req.session_id, "error": str(e)[:500]}, ensure_ascii=False)
        yield f"data: {err}\n\n"

    yield "data: [DONE]\n\n"
