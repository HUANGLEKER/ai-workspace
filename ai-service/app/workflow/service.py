from langgraph.graph import StateGraph, END
from langchain_core.messages import HumanMessage
from typing import TypedDict, Any
from app.llm.provider import get_chat_llm
from app.models.workflow import WorkflowRunRequest, WorkflowRunResponse


class WorkflowState(TypedDict):
    inputs: dict[str, Any]
    outputs: dict[str, Any]


def _build_default_graph(model: str | None):
    """Default single-node workflow: pass inputs to LLM and return output."""
    llm = get_chat_llm(model=model)

    def process(state: WorkflowState) -> WorkflowState:
        prompt = state["inputs"].get("prompt", "")
        response = llm.invoke([HumanMessage(content=prompt)])
        return {"inputs": state["inputs"], "outputs": {"result": response.content}}

    graph = StateGraph(WorkflowState)
    graph.add_node("process", process)
    graph.set_entry_point("process")
    graph.add_edge("process", END)
    return graph.compile()


async def run_workflow(req: WorkflowRunRequest) -> WorkflowRunResponse:
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
    except Exception as e:
        return WorkflowRunResponse(
            session_id=req.session_id,
            workflow_id=req.workflow_id,
            outputs={"error": str(e)},
            status="failed",
        )
