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
      <!-- 版本切换（P3-4）：链长 > 1 时显示 v1..vN，末位为当前 -->
      <div v-if="versionChain.length > 1" class="flex shrink-0 items-center gap-0.5">
        <button
          v-for="(_, i) in versionChain"
          :key="i"
          class="rounded-md px-1.5 py-0.5 text-[11px] font-medium transition-colors"
          :class="(viewingIdx === null ? i === versionChain.length - 1 : viewingIdx === i)
            ? 'bg-zinc-200 text-zinc-800 dark:bg-zinc-700 dark:text-zinc-100'
            : 'text-zinc-400 hover:bg-zinc-100 hover:text-zinc-700 dark:hover:bg-zinc-800'"
          :title="i === versionChain.length - 1 ? '当前版本' : `历史版本 v${i + 1}`"
          @click="viewingIdx = i === versionChain.length - 1 ? null : i"
        >
          v{{ i + 1 }}
        </button>
      </div>
      <div class="flex shrink-0 items-center gap-0.5">
        <AppTooltip v-if="disp && (disp.type === 'html' || disp.type === 'mermaid')" :content="rawView ? '预览' : '查看源码'">
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

    <!-- 历史版本提示条 -->
    <div
      v-if="isHistory"
      class="flex shrink-0 items-center justify-between border-b border-amber-200/70 bg-amber-50 px-3 py-1.5 text-xs text-amber-700 dark:border-amber-900/50 dark:bg-amber-950/30 dark:text-amber-400"
    >
      <span>正在查看历史版本 v{{ (viewingIdx ?? 0) + 1 }}（只读）</span>
      <button class="rounded-md px-2 py-0.5 font-medium hover:bg-amber-100 dark:hover:bg-amber-900/40" @click="viewingIdx = null">回到最新</button>
    </div>

    <!-- 预览区 -->
    <div class="flex-1 overflow-auto">
      <div v-if="!disp" class="flex h-full items-center justify-center text-sm text-zinc-400">暂无可预览内容</div>

      <!-- HTML：默认沙箱 iframe 预览，可切源码 -->
      <iframe
        v-else-if="disp.type === 'html' && !rawView"
        ref="frame"
        class="h-full w-full border-0 bg-white"
        sandbox="allow-scripts"
        :srcdoc="disp.content"
      />

      <!-- Mermaid：默认渲染图，可切源码 -->
      <div v-else-if="disp.type === 'mermaid' && !rawView" class="p-4">
        <MermaidView :code="disp.content" :dark="isDark" />
      </div>

      <!-- Markdown / PRD：富文本渲染 -->
      <div v-else-if="disp.type === 'markdown' || disp.type === 'prd'" class="p-5">
        <MarkdownView :content="disp.content" />
      </div>

      <!-- SQL / JSON / 长代码 / 源码视图：高亮 pre -->
      <pre v-else class="m-0 h-full overflow-auto bg-zinc-900 p-4 text-[13px] leading-relaxed"><code ref="codeEl" :class="`hljs language-${hlLang}`" /></pre>
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
  /** 版本链（P3-4）：id → 历史快照（不含当前实时版本） */
  versions?: Record<string, Artifact[]>
}>()
defineEmits<{ select: [id: string]; close: []; 'toggle-fullscreen': [] }>()

const { isDark } = storeToRefs(useThemeStore())
const rawView = ref(false)
const copied = ref(false)
const codeEl = ref<HTMLElement>()

// 版本链：历史快照 + 当前实时版本（末位）。viewingIdx=null 表示看最新（随流式更新）
const versionChain = computed<Artifact[]>(() => {
  const a = props.active
  if (!a) return []
  return [...(props.versions?.[a.id] || []), a]
})
const viewingIdx = ref<number | null>(null)
// 实际展示的 Artifact：选中历史版本则显示该快照，否则显示当前（active）
const disp = computed<Artifact | null>(() => {
  if (viewingIdx.value === null) return props.active
  return versionChain.value[viewingIdx.value] ?? props.active
})
const isHistory = computed(() => viewingIdx.value !== null && viewingIdx.value < versionChain.value.length - 1)

// highlight.js 语言名映射（mmd→源码用 text；html/json/sql 直传）
const hlLang = computed(() => {
  const a = disp.value
  if (!a) return 'text'
  if (a.type === 'mermaid') return 'text'
  return a.language || 'text'
})

// 切换 Artifact 时回到默认预览视图并回到最新版本
watch(() => props.activeId, () => { rawView.value = false; copied.value = false; viewingIdx.value = null })

/** 高亮当前代码型 Artifact；md/prd/html-preview/mermaid-preview 不走此路径 */
async function highlight() {
  await nextTick()
  const el = codeEl.value
  if (!el || !disp.value) return
  const lang = hlLang.value
  const code = disp.value.content
  try {
    el.innerHTML = hljs.getLanguage(lang)
      ? hljs.highlight(code, { language: lang }).value
      : hljs.highlightAuto(code).value
  } catch {
    el.textContent = code
  }
}

// 内容或视图变化时重渲高亮（含版本切换；仅在 pre 路径挂载了 codeEl 时生效）
watch(
  () => [disp.value?.content, disp.value?.id, viewingIdx.value, rawView.value],
  () => highlight(),
  { immediate: true }
)

async function copy() {
  if (!disp.value) return
  try {
    await navigator.clipboard.writeText(disp.value.content)
    copied.value = true
    setTimeout(() => (copied.value = false), 1500)
  } catch {
    toast.error('复制失败')
  }
}

function download() {
  const a = disp.value
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
