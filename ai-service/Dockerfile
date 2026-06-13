# FastAPI AI 服务：uv 官方镜像，按 uv.lock 冻结安装
# 容器为 Linux 环境，顺带解决嵌入管道 /tmp 路径的 POSIX 依赖（Windows 原生不可用）
FROM ghcr.io/astral-sh/uv:python3.14-bookworm-slim
WORKDIR /app
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy
# 先装依赖层（仅清单变化时才重建）
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-install-project --no-dev
COPY . .
RUN uv sync --frozen --no-dev
EXPOSE 8001
HEALTHCHECK --interval=15s --timeout=3s --retries=5 \
  CMD uv run python -c "import urllib.request;urllib.request.urlopen('http://localhost:8001/health')" || exit 1
# 配置经环境变量注入（pydantic BaseSettings），容器内不依赖 .env 文件
CMD ["uv", "run", "python", "main.py"]
