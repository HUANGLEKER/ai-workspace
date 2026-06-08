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
    """运行 Agent：绑定工具后执行有界的思考→行动循环，返回答案与执行轨迹。"""
    steps: list[dict] = []  # 记录每一步（LLM 输出、工具调用、工具结果、告警）

    # 构建 HTTP 工具 + 加载 MCP 工具，并建立按名查找的映射
    tools = [_build_http_tool(t) for t in req.tools]
    tools += await _load_mcp_tools(req.mcp_servers, steps)
    tool_map = {t.name: t for t in tools}

    # 有工具则绑定到模型，让其可发起工具调用
    llm = get_chat_llm(model=req.model)
    runnable = llm.bind_tools(tools) if tools else llm

    # 组装初始消息：可选系统提示 + 用户输入
    messages: list = []
    if req.system_prompt:
        messages.append(SystemMessage(content=req.system_prompt))
    messages.append(HumanMessage(content=req.input))

    output = ""
    for _ in range(MAX_ITERATIONS):
        # 思考：调用 LLM
        ai = await runnable.ainvoke(messages)
        messages.append(ai)
        if ai.content:
            output = ai.content if isinstance(ai.content, str) else str(ai.content)
            steps.append({"type": "llm", "content": output})

        # 无工具调用则视为已得出最终答案，结束循环
        tool_calls = getattr(ai, "tool_calls", None) or []
        if not tool_calls:
            break

        # 行动：依次执行模型请求的每个工具调用，并将结果回填为 ToolMessage
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
                except Exception as e:  # noqa: BLE001 —— 将工具错误回传给模型而非中断
                    result = f"工具执行出错: {e}"
            result_str = str(result)
            steps.append({"type": "tool_result", "tool": name, "content": result_str[:2000]})
            messages.append(ToolMessage(content=result_str, tool_call_id=tc.get("id", name)))

    return AgentRunResponse(session_id=req.session_id, output=output, steps=steps)
