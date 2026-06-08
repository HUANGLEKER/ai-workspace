"""Agent 路由：暴露 POST /agent/run，执行一次工具调用 Agent 并返回结果与轨迹。"""
from fastapi import APIRouter
from app.models.agent import AgentRunRequest
from app.agent.service import run_agent
from app.utils.response import Result

router = APIRouter(prefix="/agent", tags=["Agent"])


@router.post("/run", response_model=Result)
async def run(req: AgentRunRequest):
    """运行 Agent 并以统一包装返回输出与执行步骤。"""
    result = await run_agent(req)
    return Result.ok(data=result.model_dump())
