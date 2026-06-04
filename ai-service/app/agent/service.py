from langgraph.graph import StateGraph, END
from langchain_core.messages import HumanMessage
from typing import TypedDict, Annotated
import operator
from app.llm.provider import get_chat_llm
from app.models.agent import AgentRunRequest, AgentRunResponse


class AgentState(TypedDict):
    messages: Annotated[list, operator.add]
    steps: list[dict]


def _build_graph(model: str | None):
    llm = get_chat_llm(model=model)

    def call_llm(state: AgentState) -> AgentState:
        response = llm.invoke(state["messages"])
        return {
            "messages": [response],
            "steps": state["steps"] + [{"type": "llm", "content": response.content}],
        }

    graph = StateGraph(AgentState)
    graph.add_node("llm", call_llm)
    graph.set_entry_point("llm")
    graph.add_edge("llm", END)
    return graph.compile()


async def run_agent(req: AgentRunRequest) -> AgentRunResponse:
    app = _build_graph(req.model)
    initial_state: AgentState = {
        "messages": [HumanMessage(content=req.input)],
        "steps": [],
    }
    result = await app.ainvoke(initial_state)
    output = result["messages"][-1].content if result["messages"] else ""
    return AgentRunResponse(
        session_id=req.session_id,
        output=output,
        steps=result.get("steps", []),
    )
