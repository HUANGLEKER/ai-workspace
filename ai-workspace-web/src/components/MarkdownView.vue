<template>
  <div class="prose prose-zinc dark:prose-invert max-w-none prose-pre:overflow-x-auto" v-html="html" />
</template>

<script setup lang="ts">
/**
 * Markdown 渲染视图组件。
 *
 * 用 computed 缓存渲染结果：仅当 content 变化时才重新解析 + 净化，
 * 因此父组件（如流式输出中的对话页）频繁重渲时，已定稿的历史消息不会被重复渲染。
 * 渲染前经 DOMPurify 净化（见 utils/markdown），防止 LLM 返回内容造成 XSS。
 */
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps<{ content: string }>()
const html = computed(() => renderMarkdown(props.content))
</script>
