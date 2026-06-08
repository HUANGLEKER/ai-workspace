"""工作流路由：暴露 POST /workflow/run，执行一次工作流并返回结果。"""
from fastapi import APIRouter
from app.models.workflow import WorkflowRunRequest
from app.workflow.service import run_workflow
from app.utils.response import Result

router = APIRouter(prefix="/workflow", tags=["Workflow"])


@router.post("/run", response_model=Result)
async def run(req: WorkflowRunRequest):
    """运行工作流并以统一包装返回输出。"""
    result = await run_workflow(req)
    return Result.ok(data=result.model_dump())
