# 前端：node 构建 + nginx 静态托管（/api 反代到 Go 后端）
FROM node:22-alpine AS build
WORKDIR /src
# 用 corepack 激活 package.json 锁定的 pnpm 版本（无需全局安装）
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
# --frozen-lockfile：锁文件与 package.json 不一致时直接失败，保证可复现构建
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --retries=5 \
  CMD wget -q -O /dev/null http://localhost/ || exit 1
