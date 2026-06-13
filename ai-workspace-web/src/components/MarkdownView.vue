<template>
  <div class="prose prose-zinc dark:prose-invert max-w-none prose-pre:overflow-x-auto" v-html="html" @click="onClick" />
</template>

<script setup lang="ts">
/**
 * Markdown 渲染视图组件。
 *
 * 用 computed 缓存渲染结果：仅当 content 变化时才重新解析 + 净化，
 * 因此父组件（如流式输出中的对话页）频繁重渲时，已定稿的历史消息不会被重复渲染。
 * 渲染前经 DOMPurify 净化（见 utils/markdown），防止 LLM 返回内容造成 XSS。
 *
 * 代码块复制：渲染产物（v-html）中每个代码块带 [data-copy] 复制按钮。
 * 这里用「事件委托」统一处理点击 —— 无需为 v-html 注入的按钮逐个绑定监听，
 * 也不依赖 Vue 响应式即可给出复制成功反馈（直接操作该按钮 DOM）。
 */
import { computed, watch } from 'vue'
import { useAsyncMarkdown } from '@/composables/useAsyncMarkdown'

/**
 * caret：流式输出光标状态
 * - 'blink'：闪烁，表示正在流式输出
 * - 'fade' ：停止闪烁并以 300ms 透明度过渡淡出（流结束时）
 * - 'none'（默认）：不渲染光标（历史定稿消息）
 *
 * 光标作为内联元素跟随内容末尾，在 DOMPurify 之后注入，属可信片段。
 */
const props = withDefaults(defineProps<{ content: string; caret?: 'blink' | 'fade' | 'none' }>(), {
  caret: 'none'
})

const caretHtml = computed(() => {
  if (props.caret === 'none') return ''
  const modifier = props.caret === 'fade' ? 'streaming-caret--fade' : 'streaming-caret--blink'
  return `<span class="streaming-caret ${modifier}" aria-hidden="true">|</span>`
})

// 解析下放 Web Worker（见 useAsyncMarkdown），主线程仅净化注入，避免流式卡顿。
// 历史定稿消息与流式输出统一走该路径；不支持 Worker 的环境自动回退同步渲染。
const { html, render } = useAsyncMarkdown()
watch(
  () => [props.content, caretHtml.value] as const,
  ([content, caret]) => render(content, caret),
  { immediate: true }
)

/** 代码块复制：事件委托，命中 [data-copy] 按钮则复制其所在代码块的正文 */
function onClick(e: MouseEvent) {
  const btn = (e.target as HTMLElement).closest('[data-copy]') as HTMLElement | null
  if (!btn) return
  const code = btn.closest('.code-block')?.querySelector('code')?.textContent ?? ''
  if (!code) return
  navigator.clipboard.writeText(code).then(() => feedback(btn), () => feedback(btn, true))
}

/** 复制反馈：临时把按钮文案切到「Copied!」并加 done 态，1.5s 后复原（纯 DOM 操作） */
function feedback(btn: HTMLElement, failed = false) {
  const label = btn.querySelector('.code-block__copy-text')
  if (!label) return
  label.textContent = failed ? 'Failed' : 'Copied!'
  btn.classList.add('code-block__copy--done')
  setTimeout(() => {
    label.textContent = 'Copy'
    btn.classList.remove('code-block__copy--done')
  }, 1500)
}
</script>
