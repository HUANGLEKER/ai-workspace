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
- 页面级布局原语（同在 `components/ui`，新页面的标准骨架）：`PageShell`（页面外壳，纯语义容器——只管 `max-width`/`gap`/纵向 flex，**不带 padding/滚动**）、`PageHeader`（标题区）、`PageToolbar`（操作/筛选条）、`MetricCard`（仪表盘指标卡）、`ResourceCard`（资源列表卡）。新页面优先复用这些，不要每页手写一套结构。
- **滚动与外边距唯一归属是 `layout/index.vue` 的 `<main>`**：非 fullPage 页由 main 负责 `overflow-y-auto + p-6`，fullPage 页（Chat/RAG，`route.meta.fullPage`）`overflow-hidden` 自管理。页面内**不要再套 `overflow-y-auto`/`p-6`**，避免双层滚动。
- 命令式反馈：`toast`（替代 ElMessage）、`confirm`/`alertBox`（替代 ElMessageBox，`Promise<boolean>` 风格），均从 `components/ui` 出口引入。单例 `AppToaster`/`AppConfirm` 挂在 `App.vue`（同时提供 Radix `TooltipProvider`）。
- **设计令牌（单一来源在 `styles/global.css` 的 `@theme` + `:root`/`.dark` CSS 变量）**：品牌色用 `primary`/`primary-hover`/`primary-fg`（对齐 ChatGPT 2025 焕新的黑白单色：主色亮色 `#171717`、暗色 `#FFFFFF`），中性面板色用 `canvas`（页面/侧栏背景）、`surface`（卡片/浮层）、`line`（边框）、`ink`/`ink-muted`（正文/次要文本）。**优先用 `bg-primary`/`bg-surface`/`border-line`/`text-ink` 等 token 工具类，它们自带亮/暗适配，无需再写 `dark:` 中性色变体**；换肤只改变量。zinc 色阶仅用于 hover/占位等增量细节。
- 其余基调：`rounded-xl/2xl`、动效 `transition-all duration-200 ease-out`。
- 移动端（<md）：侧栏 `Sidebar` 为抽屉（`fixed` + `-translate-x-full`，`md:` 起回归常驻），由 `Header` 汉堡按钮经 `layout` 的 `mobileNavOpen` 控制，路由切换自动收起。新增需固定定位的浮层注意 z 轴层级（遮罩 `z-[6999]`、抽屉 `z-[7000]`）。
- 文件上传统一用 `AppUpload`（原生 fetch + FormData，手动注入 Authorization 头）。

## 目录结构（`src/`）

- `api/` —— 各功能的 Axios HTTP 客户端模块；`sse.ts` 为共用 SSE 工具，`request.ts` 为 Axios 实例（拦截器注入 token、统一错误处理）
- `views/` —— 页面组件（chat / knowledge[base/document/rag] / file / agent / workflow / mcp / prompt / tool / dashboard / monitor / system[user/job/model] / login）
- `components/ui/` —— 统一组件库；`components/chat/` —— 复用的对话组件（ChatMessageList/RagSources/PromptPicker/Thinking/Artifact 等）；其中 **Chat 与 RAG 两页共用的布局骨架**抽成 `Conversation*`：`ConversationShell`（两栏外壳 + 可折叠会话侧栏，折叠态经插槽作用域暴露）、`ConversationList`（会话项：选中/内联重命名/删除/折叠）、`ConversationMessages`（消息流 + Artifact 分屏，`#composer` 插槽 + 暴露 `scrollToBottom`/`scheduleScroll`）、`ConversationComposer`（输入区 + 键盘交互）。两页只保留各自 store、流式管线与标题/空态。`components/workflow/WorkflowCanvas.vue` —— Vue Flow 画布
- `composables/` —— 可复用组合式函数（流式 markdown、滚动、动效、artifact 面板等）
- `stores/` —— Pinia（auth / chat / rag / theme）
- `router/` —— 路由配置（守卫加载用户信息，非管理员隐藏系统菜单）
- `layout/` —— 外壳/布局；侧边栏菜单的**单一数据源**是 `layout/navigation.ts`（`navGroups` 分组配置 + `getVisibleNavGroups(isAdmin)` 过滤 `admin` 分组 + `isNavItemActive` 高亮判定）。增删菜单只改这里，勿在 `Sidebar.vue` 内硬编码
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
