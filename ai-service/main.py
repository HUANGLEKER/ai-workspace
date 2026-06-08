"""FastAPI AI 服务入口。

负责装配 FastAPI 应用：注册 CORS 中间件、挂载所有业务路由、设置全局异常处理，
并暴露健康检查端点。该服务由 Spring Boot 后端通过 HTTP 内部调用，
统一处理所有 LLM 相关交互（Chat / RAG / Embedding / Agent / Workflow）。
"""
import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from app.api.router import api_router
from app.config.settings import settings

logger = logging.getLogger("ai-service")

app = FastAPI(
    title="AI Workspace Service",
    description="FastAPI AI Service — Chat / RAG / Embedding / Agent / Workflow",
    version="1.0.0",
)

# 注册 CORS 中间件
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origin_list,
    # 携带凭证（credentials）不能与通配符 origin 组合（浏览器会拒绝），
    # 且本服务通过请求头而非 Cookie 鉴权，故关闭凭证。
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 挂载聚合路由（Chat / Embedding / RAG / Agent / Workflow）
app.include_router(api_router)


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    """请求体校验失败处理：记录日志并返回 422 与详细错误。"""
    logger.error("Validation error on %s %s: %s", request.method, request.url.path, exc.errors())
    return JSONResponse(status_code=422, content={"detail": exc.errors()})


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    """全局兜底异常处理：记录堆栈并以统一包装格式返回 500。"""
    logger.exception("Unhandled error on %s %s", request.method, request.url.path)
    return JSONResponse(
        status_code=500,
        content={"code": 500, "message": str(exc), "data": None},
    )


@app.get("/health", tags=["Health"])
async def health():
    """健康检查端点，供 Spring Boot 监控模块探活。"""
    return {"code": 200, "message": "ok"}


if __name__ == "__main__":
    # 以 uvicorn 运行；开发环境（app_debug=True）开启自动重载
    import uvicorn
    uvicorn.run("main:app", host=settings.app_host, port=settings.app_port, reload=settings.app_debug)
