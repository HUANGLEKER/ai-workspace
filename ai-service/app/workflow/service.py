"""工作流引擎，基于 LangGraph。

当前实现为默认的单节点工作流：取输入中的 prompt 调用 LLM 并返回结果，
后续可扩展为多节点有向图。
"""
from langgraph.graph import StateGraph, END
from langchain_core.messages import HumanMessage
from typing import TypedDict, Any
from app.llm.provider import get_chat_llm
from app.models.workflow import WorkflowRunRequest, WorkflowRunResponse


class WorkflowState(TypedDict):
    """工作流在节点间流转的状态：输入与输出两部分。"""
    inputs: dict[str, Any]
    outputs: dict[str, Any]


def _build_default_graph(model: str | None):
    """默认单节点工作流：将输入传给 LLM 并返回其输出。"""
    llm = get_chat_llm(model=model)

    def process(state: WorkflowState) -> WorkflowState:
        """处理节点：取 prompt 调用 LLM，结果写入 outputs.result。"""
        prompt = state["inputs"].get("prompt", "")
        response = llm.invoke([HumanMessage(content=prompt)])
        return {"inputs": state["inputs"], "outputs": {"result": response.content}}

    # 构建图：process 作为唯一节点，入口 → process → 结束
    graph = StateGraph(WorkflowState)
    graph.add_node("process", process)
    graph.set_entry_point("process")
    graph.add_edge("process", END)
    return graph.compile()


async def run_workflow(req: WorkflowRunRequest) -> WorkflowRunResponse:
    """执行工作流；捕获异常并以 failed 状态返回错误信息。"""
    try:
        app = _build_default_graph(req.model)
        initial_state: WorkflowState = {"inputs": req.inputs, "outputs": {}}
        result = await app.ainvoke(initial_state)
        return WorkflowRunResponse(
            session_id=req.session_id,
            workflow_id=req.workflow_id,
            outputs=result.get("outputs", {}),
            status="completed",
        )
    except Exception as e:  # 任意环节失败均不向上抛出，统一转为 failed 状态响应，由调用方决策重试策略
        return WorkflowRunResponse(
            session_id=req.session_id,
            workflow_id=req.workflow_id,
            outputs={"error": str(e)},
            status="failed",
        )
