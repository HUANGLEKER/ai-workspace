// Package tracing 提供基于 OpenTelemetry 的分布式链路追踪初始化。
//
// 目标：串联 浏览器 → Gin 网关 → FastAPI AI 服务 的全链路，便于在 SSE 断流 / 响应缓慢时
// 快速定界是网关慢还是大模型慢。Gin 侧用 otelgin 中间件起根 span（或延续上游 traceparent），
// 出站到 FastAPI 的 HTTP 请求经全局 TextMapPropagator 注入 W3C traceparent 头，
// FastAPI 侧的 OTel 自动埋点据此续接同一条 trace。
//
// 采用 W3C tracecontext 传播标准，与既有的 X-Request-ID 关联头并存（前者用于 APM 后端
// 聚合 span，后者用于纯文本日志 grep 串联）。导出走 OTLP/HTTP，端点可配（如 Jaeger/Tempo/
// OTel Collector）；未配置端点时 Init 返回 no-op，不引入任何运行时开销。
package tracing

import (
	"context"
	"time"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.26.0"
)

// Init 初始化全局 TracerProvider 与传播器。
//
// endpoint 为 OTLP/HTTP 采集端点（host:port，如 "localhost:4318"）；为空时不启用追踪，
// 返回的 shutdown 为无副作用的空函数。serviceName 标识本服务（在 APM 中作为 service.name）。
// 返回的 shutdown 应在进程退出前调用，以 flush 残留 span。
func Init(serviceName, endpoint string) (shutdown func(context.Context) error, err error) {
	noop := func(context.Context) error { return nil }
	if endpoint == "" {
		return noop, nil
	}

	// OTLP/HTTP 导出器；本地 / 内网默认明文传输（生产应置于可信网络或加 TLS）。
	exporter, err := otlptracehttp.New(context.Background(),
		otlptracehttp.WithEndpoint(endpoint),
		otlptracehttp.WithInsecure(),
	)
	if err != nil {
		return noop, err
	}

	res, err := resource.New(context.Background(),
		resource.WithAttributes(semconv.ServiceName(serviceName)),
	)
	if err != nil {
		return noop, err
	}

	tp := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(exporter, sdktrace.WithBatchTimeout(5*time.Second)),
		sdktrace.WithResource(res),
	)
	otel.SetTracerProvider(tp)
	// 复合传播器：W3C tracecontext + baggage，跨服务透传 trace 上下文。
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{},
		propagation.Baggage{},
	))

	return tp.Shutdown, nil
}
