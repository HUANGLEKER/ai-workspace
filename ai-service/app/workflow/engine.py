"""工作流执行引擎（P3-1）。

执行画布序列化的 definition JSON：按拓扑顺序逐节点运行，节点输出以 node id 存入
共享变量表，供下游节点用 {{nodeId}} 模板引用。definition 为空时回退默认单节点（向后兼容）。

definition schema:
    {
      "nodes": [{"id": "...", "type": "start|llm|http|end", "data": {...}}],
      "edges": [{"source": "id", "target": "id"}]
    }
节点 data 约定：
    start: 无（入口，把 inputs 注入变量表，含 inputs.input/inputs.prompt）
    llm:   {"prompt": "模板，可含 {{input}} {{节点id}}"}
    http:  {"method": "GET|POST", "url": "...", "body": "模板字符串"}
    end:   无（终点，把上游变量作为最终 outputs.result）
"""
import json
import re
from typing import Any

import httpx
from langchain_core.messages import HumanMessage

from app.llm.provider import get_chat_llm

_VAR_RE = re.compile(r"\{\{\s*([\w.]+)\s*\}\}")
MAX_NODES = 50  # 单次执行节点数上限，防御异常大图


def _render(template: str, vars: dict[str, Any]) -> str:
    """用变量表替换模板中的 {{key}}；未命中的占位符替换为空串。"""
    return _VAR_RE.sub(lambda m: str(vars.get(m.group(1), "")), template or "")


def _topo_order(nodes: list[dict], edges: list[dict]) -> list[str]:
    """对节点做拓扑排序，返回执行顺序的 node id 列表；有环或缺节点时抛 ValueError。"""
    ids = [n["id"] for n in nodes]
    idset = set(ids)
    indeg = {i: 0 for i in ids}
    adj: dict[str, list[str]] = {i: [] for i in ids}
    for e in edges:
        src, dst = e.get("source"), e.get("target")
        if src not in idset or dst not in idset:
            continue  # 悬空边忽略
        adj[src].append(dst)
        indeg[dst] += 1
    # Kahn 算法，入度 0 的节点按原始顺序入队（保证确定性）
    queue = [i for i in ids if indeg[i] == 0]
    order: list[str] = []
    while queue:
        cur = queue.pop(0)
        order.append(cur)
        for nxt in adj[cur]:
            indeg[nxt] -= 1
            if indeg[nxt] == 0:
                queue.append(nxt)
    if len(order) != len(ids):
        raise ValueError("工作流存在环或不可达节点")
    return order


async def _run_node(node: dict, vars: dict[str, Any], model: str | None) -> str:
    """执行单个节点，返回其输出文本。"""
    ntype = node.get("type")
    data = node.get("data") or {}

    if ntype in ("start", "end"):
        # start 已在初始化时注入 inputs；end 透传上游结果
        return str(vars.get("input", ""))

    if ntype == "llm":
        prompt = _render(str(data.get("prompt", "")), vars)
        llm = get_chat_llm(model=model)
        resp = await llm.ainvoke([HumanMessage(content=prompt)])
        return resp.content if isinstance(resp.content, str) else str(resp.content)

    if ntype == "http":
        method = str(data.get("method", "GET")).upper()
        url = _render(str(data.get("url", "")), vars)
        body_tpl = data.get("body")
        async with httpx.AsyncClient(timeout=30.0) as client:
            if method == "GET":
                resp = await client.get(url)
            else:
                payload = _render(str(body_tpl), vars) if body_tpl else None
                # body 模板渲染后若是合法 JSON 则按 JSON 发送，否则按原始文本
                try:
                    resp = await client.request(method, url, json=json.loads(payload) if payload else None)
                except (json.JSONDecodeError, TypeError):
                    resp = await client.request(method, url, content=payload)
            resp.raise_for_status()
            return resp.text[:4000]

    raise ValueError(f"不支持的节点类型: {ntype}")


async def run_definition(definition: str, inputs: dict[str, Any], model: str | None) -> dict[str, Any]:
    """执行 definition 图，返回 outputs。definition 为空/无节点时回退默认单节点 LLM。"""
    spec = _parse(definition)
    nodes = spec.get("nodes") if spec else None

    if not nodes:
        # 向后兼容：无定义时等价于原默认图——取 prompt 调 LLM
        prompt = inputs.get("prompt") or inputs.get("input") or ""
        llm = get_chat_llm(model=model)
        resp = await llm.ainvoke([HumanMessage(content=prompt)])
        return {"result": resp.content if isinstance(resp.content, str) else str(resp.content)}

    if len(nodes) > MAX_NODES:
        raise ValueError(f"节点数超过上限 {MAX_NODES}")

    node_map = {n["id"]: n for n in nodes}
    order = _topo_order(nodes, spec.get("edges") or [])

    # 变量表初始化：input/prompt 均指向用户输入，便于模板引用
    user_input = inputs.get("input") or inputs.get("prompt") or ""
    vars: dict[str, Any] = {"input": user_input, "prompt": user_input, **inputs}

    last_output = ""
    for nid in order:
        node = node_map[nid]
        out = await _run_node(node, vars, model)
        vars[nid] = out
        # result 取最后一个「实质」节点（llm/http）的输出，start/end 只透传不计入
        if node.get("type") not in ("start", "end"):
            last_output = out

    return {"result": last_output, "vars": {k: v for k, v in vars.items() if k in node_map}}


def _parse(definition: str) -> dict | None:
    """解析 definition JSON，非法/空时返回 None。"""
    if not definition or not definition.strip():
        return None
    try:
        spec = json.loads(definition)
        return spec if isinstance(spec, dict) else None
    except json.JSONDecodeError:
        return None
