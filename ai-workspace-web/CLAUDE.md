# CLAUDE.md —— 前端（ai-workspace-web）

> 本文件仅覆盖前端子项目。跨服务约定、整体架构见仓库根目录 `CLAUDE.md`。

## 技术栈

Vue 3 + TypeScript + Vite 6 + Pinia + Vue Router 4。UI 栈：**TailwindCSS 4（`@tailwindcss/vite` + `@tailwindcss/typography`）+ Radix Vue（headless 交互）+ lucide-vue-next（图标）**。包管理器为 **pnpm**（版本由 `package.json` 的 `packageManager` 锁定，`corepack enable` 激活）。

## 常用命令

```bash
pnpm install
pnpm dev                      # 开发服务器：3000，代理 /api → localhost:8080
pnpm build                    # 生产构建（先 vue-tsc 再 vite build）
pnpm exec vue-tsc --noEmit    # 仅类型检查
pnpm test                     # vitest run（单元测试）
pnpm test:watch               # vitest watch
```

## UI 规范（强约束）

- **禁止**引入 Element Plus / 其他组件库或图标集；**禁止** `<style scoped>`、`::v-deep`、`!important`。所有样式用 Tailwind utility class。
- 统一组件库在 `src/components/ui`（AppButton/AppInput/AppDialog/AppTable/AppSelect/AppPagination/AppUpload 等），经 `index.ts` 出口统一引入。
- 命令式反馈：`toast`（替代 ElMessage）、`confirm`/`alertBox`（替代 ElMessageBox，`Promise<boolean>` 风格），均从 `components/ui` 出口引入。单例 `AppToaster`/`AppConfirm` 挂在 `App.vue`（同时提供 Radix `TooltipProvider`）。
- 设计基调：zinc 色阶、`rounded-xl/2xl`、`border-zinc-200/80`、品牌色 `bg-zinc-900 text-white`、动效 `transition-all duration-200 ease-out`。
- 文件上传统一用 `AppUpload`（原生 fetch + FormData，手动注入 Authorization 头）。

## 目录结构（`src/`）

- `api/` —— 各功能的 Axios HTTP 客户端模块；`sse.ts` 为共用 SSE 工具，`request.ts` 为 Axios 实例（拦截器注入 token、统一错误处理）
- `views/` —— 页面组件（chat / knowledge[base/document/rag] / file / agent / workflow / mcp / prompt / tool / dashboard / monitor / system[user/job/model] / login）
- `components/ui/` —— 统一组件库；`components/chat/` —— 复用的对话组件（ChatMessageList/RagSources/PromptPicker/Thinking/Artifact 等）；`components/workflow/WorkflowCanvas.vue` —— Vue Flow 画布
- `composables/` —— 可复用组合式函数（流式 markdown、滚动、动效、artifact 面板等）
- `stores/` —— Pinia（auth / chat / rag / theme）
- `router/` —— 路由配置（守卫加载用户信息，非管理员隐藏系统菜单）
- `layout/` —— 外壳/布局
- `utils/` —— markdown 渲染（含 worker）、highlight、artifacts、promptVars
- `types/` —— TS 类型定义

## SSE 流式（核心约定）

Chat / RAG 流式用原生 `fetch()`（Axios 不支持 SSE）。所有 SSE 消费方共用 `api/sse.ts:streamSSE`，它统一处理鉴权头、UTF-8 增量解码、`\n` 行缓冲、`data: <json>` 解析、`data: [DONE]` 哨兵、`AbortSignal` 取消。调用方只提供 URL/body、`extract` 映射器（载荷→展示文本）与 `onChunk/onDone/onError`。

- `api/chat.ts:sendMessageStream` 是其薄封装。
- 新流式端点（如 RAG `api/kb.ts:ragChatStream`）**必须复用 `streamSSE`** 并自定义 `extract`，不要重写读取循环。
- RAG 问答页（`views/knowledge/rag`，状态在 `stores/rag.ts`）复用 chat 组件，`onMeta` 旁路分拣 `{type:'sources'|'usage'|'title'|'status'}` 帧。

## 打包

`build.rollupOptions.output.manualChunks` 仅强制拆分框架核心（`vue-vendor`）与较重的 markdown 栈（`markdown`，随 chat/RAG 懒加载）。

## 测试

vitest（happy-dom/jsdom）。现有测试集中在 `utils/__tests__`、`api/__tests__`、`composables/__tests__`。改动公共工具（sse/markdown/promptVars/artifact panel）后请跑 `pnpm test`。
