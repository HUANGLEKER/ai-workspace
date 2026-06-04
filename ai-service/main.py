from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from app.api.router import api_router
from app.config.settings import settings

app = FastAPI(
    title="AI Workspace Service",
    description="FastAPI AI Service — Chat / RAG / Embedding / Agent / Workflow",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api_router)


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    return JSONResponse(
        status_code=500,
        content={"code": 500, "message": str(exc), "data": None},
    )


@app.get("/health", tags=["Health"])
async def health():
    return {"code": 200, "message": "ok"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host=settings.app_host, port=settings.app_port, reload=settings.app_debug)
