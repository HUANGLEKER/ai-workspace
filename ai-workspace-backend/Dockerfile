# Go 后端多阶段构建：静态编译 + 最小运行时
FROM golang:1.26-alpine AS build
WORKDIR /src
# 先拷贝依赖清单以复用模块下载缓存层
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -ldflags="-s -w" -o /out/server ./cmd/server

FROM alpine:3.20
# ca-certificates：出站 HTTPS（LLM 提供方）；tzdata：日志本地时区；wget：healthcheck
RUN apk add --no-cache ca-certificates tzdata wget
WORKDIR /app
COPY --from=build /out/server ./server
# 默认配置（本机开发用）；容器编排时由 compose 挂载 config.docker.yaml 覆盖
COPY config.yaml ./config.yaml
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --retries=5 \
  CMD wget -q -O /dev/null http://localhost:8080/health || exit 1
ENTRYPOINT ["./server", "-config", "config.yaml"]
