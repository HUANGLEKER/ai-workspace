"""可观测性（P2-1）：请求 ID 贯穿 + LLM 调用指标。

- 请求 ID：Go 后端经 X-Request-ID 头透传，HTTP 中间件存入 contextvar，
  日志过滤器将其注入每条日志记录（rid 字段），三服务日志可按 rid 串联。
- LLM 指标：每次 LLM 调用结束后记一条结构化日志（模型、耗时、token 用量、
  是否失败），是后续用量聚合（usage_daily）与成本分析的数据源。
"""
import contextvars
import json
import logging
import sys
import time

REQUEST_ID_HEADER = "X-Request-ID"

request_id_var: contextvars.ContextVar[str] = contextvars.ContextVar("request_id", default="-")


class RequestIdFilter(logging.Filter):
    """把当前请求 ID 注入日志记录（record.rid）。"""

    def filter(self, record: logging.LogRecord) -> bool:
        record.rid = request_id_var.get()
        return True


def setup_logging() -> None:
    """为业务日志（ai-service / llm）配置带 rid 的结构化输出。

    不动 uvicorn 自带的 access 日志；幂等（自动重载下重复调用安全）。
    """
    formatter = logging.Formatter(
        fmt='{"time":"%(asctime)s","level":"%(levelname)s","logger":"%(name)s","rid":"%(rid)s","msg":%(message)s}',
        datefmt="%Y-%m-%dT%H:%M:%S",
    )
    for name in ("ai-service", "llm"):
        logger = logging.getLogger(name)
        if logger.handlers:  # 已配置过（reload 场景）
            continue
        handler = logging.StreamHandler(sys.stdout)
        handler.setFormatter(formatter)
        handler.addFilter(RequestIdFilter())
        logger.addHandler(handler)
        logger.setLevel(logging.INFO)
        logger.propagate = False


_llm_logger = logging.getLogger("llm")


class LlmCallTimer:
    """LLM 调用计时与指标记录。

    用法：
        timer = LlmCallTimer("chat", model)
        try:
            ... 流式生成，期间 timer.set_usage(usage) ...
        except Exception as e:
            timer.fail(e); raise
        else:
            timer.done()
    """

    def __init__(self, kind: str, model: str | None):
        self.kind = kind
        self.model = model or "(default)"
        self.start = time.monotonic()
        self.usage: dict | None = None

    def set_usage(self, usage: dict) -> None:
        self.usage = usage

    def _payload(self, status: str) -> dict:
        p = {
            "event": "llm_call",
            "kind": self.kind,
            "model": self.model,
            "status": status,
            "duration_ms": round((time.monotonic() - self.start) * 1000),
        }
        if self.usage:
            p["prompt_tokens"] = self.usage.get("input_tokens", 0)
            p["completion_tokens"] = self.usage.get("output_tokens", 0)
            p["total_tokens"] = self.usage.get("total_tokens", 0)
        return p

    def done(self) -> None:
        _llm_logger.info(json.dumps(self._payload("ok"), ensure_ascii=False))

    def fail(self, err: Exception) -> None:
        p = self._payload("error")
        p["error"] = str(err)[:500]
        _llm_logger.error(json.dumps(p, ensure_ascii=False))
