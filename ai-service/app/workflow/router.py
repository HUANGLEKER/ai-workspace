from fastapi import APIRouter
from app.models.workflow import WorkflowRunRequest
from app.workflow.service import run_workflow
from app.utils.response import Result

router = APIRouter(prefix="/workflow", tags=["Workflow"])


@router.post("/run", response_model=Result)
async def run(req: WorkflowRunRequest):
    result = await run_workflow(req)
    return Result.ok(data=result.model_dump())
