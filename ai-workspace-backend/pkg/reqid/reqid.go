// Package reqid 提供请求 ID 的 context 存取（P2-1 链路追踪）。
// 独立成中立小包：internal/middleware 写入、pkg/fastapi 读取并透传下游，
// 避免 pkg 层反向依赖 internal。
package reqid

import "context"

type ctxKey struct{}

// Header 是贯穿 浏览器→Go→FastAPI 的请求 ID 头名
const Header = "X-Request-ID"

// With 将请求 ID 写入 context
func With(ctx context.Context, id string) context.Context {
	return context.WithValue(ctx, ctxKey{}, id)
}

// From 从 context 取请求 ID，未设置时返回空串
func From(ctx context.Context) string {
	v, _ := ctx.Value(ctxKey{}).(string)
	return v
}
