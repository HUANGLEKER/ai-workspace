<template>
  <div
    class="flex h-full flex-col bg-white dark:bg-zinc-900"
    :class="fullscreen ? 'fixed inset-0 z-50' : ''"
  >
    <!-- 顶栏：标题 + 标签页 + 操作（Copy / Download / 全屏 / 关闭） -->
    <div class="flex h-12 shrink-0 items-center gap-2 border-b border-zinc-200/80 px-3 dark:border-zinc-800">
      <LayoutPanelLeft class="h-4 w-4 shrink-0 text-zinc-400" />
      <!-- 多 Artifact 时以标签页切换 -->
      <div class="flex flex-1 items-center gap-1 overflow-x-auto">
        <button
          v-for="a in artifacts"
          :key="a.id"
          class="shrink-0 rounded-lg px-2.5 py-1 text-xs font-medium transition-colors duration-200"
          :class="a.id === activeId
            ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
            : 'text-zinc-500 hover:bg-zinc-100 hover:text-zinc-800 dark:text-zinc-400 dark:hover:bg-zinc-800'"
          @click="$emit('select', a.id)"
        >
          {{ a.title }}
        </button>
      </div>
      <div class="flex shrink-0 items-center gap-0.5">
        <AppTooltip v-if="active && (active.type === 'html' || active.type === 'mermaid')" :content="rawView ? '预览' : '查看源码'">
          <button class="rounded-lg p-1.5 text-zinc-400 transition-colors hover:bg-zinc-100 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200" @click="rawView = !rawView">
            <Code v-if="!rawView" class="h-4 w-4" />
            <Eye v-else class="h-4 w-4" />
          </button>
        </AppTooltip>
        <AppTooltip content="复制">
          <button class="rounded-lg p-1.5 text-zinc-400 transition-colors hover:bg-zinc-100 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200" @click="copy">
            <Check v-if="copied" class="h-4 w-4 text-emerald-500" />
            <Copy v-else class="h-4 w-4" />
          </button>
        </AppTooltip>
        <AppTooltip content="下载">
          <button class="rounded-lg p-1.5 text-zinc-400 transition-colors hover:bg-zinc-100 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200" @click="download">
            <Download class="h-4 w-4" />
          </button>
        </AppTooltip>
        <AppTooltip :content="fullscreen ? '退出全屏' : '全屏'">
          <button class="rounded-lg p-1.5 text-zinc-400 transition-colors hover:bg-zinc-100 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200" @click="$emit('toggle-fullscreen')">
            <Minimize2 v-if="fullscreen" class="h-4 w-4" />
            <Maximize2 v-else class="h-4 w-4" />
          </button>
        </AppTooltip>
        <AppTooltip content="关闭">
          <button class="rounded-lg p-1.5 text-zinc-400 transition-colors hover:bg-zinc-100 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200" @click="$emit('close')">
            <X class="h-4 w-4" />
          </button>
        </AppTooltip>
      </div>
    </div>

    <!-- 预览区 -->
    <div class="flex-1 overflow-auto">
      <div v-if="!active" class="flex h-full items-center justify-center text-sm text-zinc-400">暂无可预览内容</div>

      <!-- HTML：默认沙箱 iframe 预览，可切源码 -->
      <iframe
        v-else-if="active.type === 'html' && !rawView"
        ref="frame"
        class="h-full w-full border-0 bg-white"
        sandbox="allow-scripts"
        :srcdoc="active.content"
      />

      <!-- Mermaid：默认渲染图，可切源码 -->
      <div v-else-if="active.type === 'mermaid' && !rawView" class="p-4">
        <MermaidView :code="active.content" :dark="isDark" />
      </div>

      <!-- Markdown / PRD：富文本渲染 -->
      <div v-else-if="active.type === 'markdown' || active.type === 'prd'" class="p-5">
        <MarkdownView :content="active.content" />
      </div>

      <!-- SQL / JSON / 长代码 / 源码视图：高亮 pre -->
      <pre v-else class="m-0 overflow-auto p-4 text-[13px] leading-relaxed"><code ref="codeEl" :class="`hljs language-${hlLang}`" /></pre>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * Feature 2：Artifact 预览面板（右侧）。
 *
 * 按 Artifact 类型选择渲染器：
 * - html    → 沙箱 iframe 实时预览（allow-scripts，禁同源，防止越权）；可切源码
 * - mermaid → MermaidView 渲图；可切源码
 * - md/prd  → MarkdownView 富文本
 * - sql/json/code → highlight.js 高亮
 *
 * 工具：Copy（复制当前 Artifact 源码）、Download（按类型扩展名下载）、全屏、关闭。
 * 实时更新：active.content 随流式刷新变化，watch 同步重渲高亮/iframe。
 */
import { ref, computed, watch, nextTick } from 'vue'
import { storeToRefs } from 'pinia'
import hljs from '@/utils/highlight'
import { Copy, Check, Download, Maximize2, Minimize2, X, LayoutPanelLeft, Code, Eye } from 'lucide-vue-next'
import { AppTooltip, toast } from '@/components/ui'
import MarkdownView from '@/components/MarkdownView.vue'
import MermaidView from './MermaidView.vue'
import { useThemeStore } from '@/stores/theme'
import { artifactExt, type Artifact } from '@/utils/artifacts'

const props = defineProps<{
  artifacts: Artifact[]
  activeId: string
  active: Artifact | null
  fullscreen: boolean
}>()
defineEmits<{ select: [id: string]; close: []; 'toggle-fullscreen': [] }>()

const { isDark } = storeToRefs(useThemeStore())
const rawView = ref(false)
const copied = ref(false)
const codeEl = ref<HTMLElement>()

// highlight.js 语言名映射（mmd→源码用 text；html/json/sql 直传）
const hlLang = computed(() => {
  const a = props.active
  if (!a) return 'text'
  if (a.type === 'mermaid') return 'text'
  return a.language || 'text'
})

// 切换 Artifact 时回到默认预览视图
watch(() => props.activeId, () => { rawView.value = false; copied.value = false })

/** 高亮当前代码型 Artifact；md/prd/html-preview/mermaid-preview 不走此路径 */
async function highlight() {
  await nextTick()
  const el = codeEl.value
  if (!el || !props.active) return
  const lang = hlLang.value
  const code = props.active.content
  try {
    el.innerHTML = hljs.getLanguage(lang)
      ? hljs.highlight(code, { language: lang }).value
      : hljs.highlightAuto(code).value
  } catch {
    el.textContent = code
  }
}

// 内容或视图变化时重渲高亮（仅在 pre 路径挂载了 codeEl 时生效）
watch(
  () => [props.active?.content, props.active?.id, rawView.value],
  () => highlight(),
  { immediate: true }
)

async function copy() {
  if (!props.active) return
  try {
    await navigator.clipboard.writeText(props.active.content)
    copied.value = true
    setTimeout(() => (copied.value = false), 1500)
  } catch {
    toast.error('复制失败')
  }
}

function download() {
  const a = props.active
  if (!a) return
  const blob = new Blob([a.content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${a.id}.${artifactExt(a)}`
  link.click()
  URL.revokeObjectURL(url)
}
</script>
