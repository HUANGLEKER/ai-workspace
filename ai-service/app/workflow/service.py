"""工作流业务逻辑：委托 engine 执行画布序列化的 definition 图。

definition 为空时 engine 回退为默认单节点（取 prompt 调 LLM），保持向后兼容。
"""
from app.models.workflow import WorkflowRunRequest, WorkflowRunResponse
from app.workflow.engine import run_definition


async def run_workflow(req: WorkflowRunRequest) -> WorkflowRunResponse:
    """执行工作流：按 definition 图运行（空则回退默认单节点）；异常转 failed 状态。"""
    try:
        outputs = await run_definition(req.definition or "", req.inputs, req.model)
        return WorkflowRunResponse(
            session_id=req.session_id,
            workflow_id=req.workflow_id,
            outputs=outputs,
            status="completed",
        )
    except Exception as e:  # 任意环节失败均不向上抛出，统一转为 failed 状态响应，由调用方决策重试策略
        return WorkflowRunResponse(
            session_id=req.session_id,
            workflow_id=req.workflow_id,
            outputs={"error": str(e)},
            status="failed",
        )
