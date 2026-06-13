"""Agent 路由：一次性 POST /agent/run 与流式 POST /agent/run/stream。"""
from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from app.models.agent import AgentRunRequest
from app.agent.service import run_agent, run_agent_stream
from app.utils.response import Result

router = APIRouter(prefix="/agent", tags=["Agent"])


@router.post("/run", response_model=Result)
async def run(req: AgentRunRequest):
    """运行 Agent 并以统一包装返回输出与执行步骤（一次性，保留向后兼容）。"""
    result = await run_agent(req)
    return Result.ok(data=result.model_dump())


@router.post("/run/stream")
async def run_stream(req: AgentRunRequest):
    """流式运行 Agent：think→act 轨迹逐步 SSE 下发，结尾 answer 帧 + [DONE]。"""
    return StreamingResponse(
        run_agent_stream(req),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
