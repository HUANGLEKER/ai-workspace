from fastapi import APIRouter
from app.models.agent import AgentRunRequest
from app.agent.service import run_agent
from app.utils.response import Result

router = APIRouter(prefix="/agent", tags=["Agent"])


@router.post("/run", response_model=Result)
async def run(req: AgentRunRequest):
    result = await run_agent(req)
    return Result.ok(data=result.model_dump())
