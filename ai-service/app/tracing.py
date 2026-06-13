"""OpenTelemetry 分布式链路追踪初始化。

与 Go 网关串联全链路：Go 出站调用注入 W3C traceparent 头，这里的 FastAPIInstrumentor
自动从入站请求头续接同一条 trace；HTTPXClientInstrumentor 再把 trace 透传给下游 LLM/
嵌入提供商的 HTTP 调用，从而在 SSE 断流 / 响应缓慢时能定界是网关、AI 服务还是大模型慢。

导出走 OTLP/HTTP，端点由 settings.tracing_endpoint 配置（如 http://localhost:4318）；
未配置时 setup_tracing 直接返回，不引入任何运行时开销。
"""
import logging

from app.config.settings import settings

logger = logging.getLogger("ai-service")


def setup_tracing(app) -> None:
    """按配置为 FastAPI 应用与 httpx 客户端启用 OTel 链路追踪。

    端点未配置时为 no-op。导入在函数内进行，避免未启用时的额外加载开销，
    也让 OTel 成为可选依赖（缺失时不影响主流程）。
    """
    endpoint = settings.tracing_endpoint
    if not endpoint:
        return
    try:
        from opentelemetry import trace
        from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
        from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
        from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor
        from opentelemetry.sdk.resources import Resource
        from opentelemetry.sdk.trace import TracerProvider
        from opentelemetry.sdk.trace.export import BatchSpanProcessor

        resource = Resource.create({"service.name": settings.tracing_service_name})
        provider = TracerProvider(resource=resource)
        # OTLP/HTTP traces 端点约定为 {endpoint}/v1/traces
        exporter = OTLPSpanExporter(endpoint=f"{endpoint.rstrip('/')}/v1/traces")
        provider.add_span_processor(BatchSpanProcessor(exporter))
        trace.set_tracer_provider(provider)

        # 入站请求自动续接上游 traceparent；下游 httpx 调用自动透传 trace
        FastAPIInstrumentor.instrument_app(app)
        HTTPXClientInstrumentor().instrument()
        logger.info("链路追踪已启用，OTLP 端点=%s", endpoint)
    except Exception:  # noqa: BLE001 - 追踪不应阻断服务启动
        logger.exception("初始化链路追踪失败，将以无追踪模式运行")
