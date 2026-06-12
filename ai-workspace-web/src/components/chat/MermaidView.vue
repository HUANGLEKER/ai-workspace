<template>
  <div class="flex w-full justify-center">
    <div v-if="error" class="w-full rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs text-amber-700 dark:border-amber-900/50 dark:bg-amber-950/30 dark:text-amber-400">
      Mermaid 渲染失败（图表可能尚未输出完整）：{{ error }}
    </div>
    <div v-else ref="host" class="mermaid-host w-full overflow-x-auto text-center" v-html="svg" />
  </div>
</template>

<script setup lang="ts">
/**
 * Mermaid 图渲染。mermaid 体积较大，按需动态 import（仅进入含图的 Artifact 时才加载）。
 * 流式期间图源可能尚未闭合 → 渲染失败时静默给出占位提示，待内容补全后自动重渲。
 *
 * 每次渲染用唯一 id，避免 mermaid 内部缓存/重复 id 冲突；渲染做了「最新优先」防抖，
 * 流式高频更新时只保留最后一次结果。
 */
import { ref, watch, onMounted } from 'vue'

const props = defineProps<{ code: string; dark?: boolean }>()

const svg = ref('')
const error = ref('')
let seq = 0

async function render() {
  const code = props.code.trim()
  if (!code) {
    svg.value = ''
    error.value = ''
    return
  }
  const mySeq = ++seq
  try {
    const mermaid = (await import('mermaid')).default
    mermaid.initialize({ startOnLoad: false, theme: props.dark ? 'dark' : 'default', securityLevel: 'strict' })
    const { svg: out } = await mermaid.render(`mermaid-${Date.now()}-${mySeq}`, code)
    if (mySeq !== seq) return // 已有更新的渲染在途，丢弃过期结果
    svg.value = out
    error.value = ''
  } catch (e) {
    if (mySeq !== seq) return
    error.value = (e as Error).message?.split('\n')[0] || '语法错误'
  }
}

onMounted(render)
watch(() => [props.code, props.dark], render)
</script>
