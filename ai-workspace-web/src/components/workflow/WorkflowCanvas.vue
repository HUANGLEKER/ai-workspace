<template>
  <div class="flex h-[60vh] gap-3">
    <!-- 画布 -->
    <div class="relative flex-1 overflow-hidden rounded-xl border border-zinc-200/80 dark:border-zinc-700">
      <!-- 工具栏 -->
      <div class="absolute left-3 top-3 z-10 flex gap-2">
        <AppButton size="sm" variant="secondary" :icon="Plus" @click="addNode('llm')">LLM 节点</AppButton>
        <AppButton size="sm" variant="secondary" :icon="Plus" @click="addNode('http')">HTTP 节点</AppButton>
        <AppButton size="sm" variant="secondary" :icon="Plus" @click="addNode('search')">搜索节点</AppButton>
      </div>
      <VueFlow
        v-model:nodes="nodes"
        v-model:edges="edges"
        :default-viewport="{ zoom: 0.9 }"
        class="h-full w-full bg-zinc-50 dark:bg-zinc-900"
        @connect="onConnect"
        @node-click="onNodeClick"
        @nodes-change="emitChange"
        @edges-change="emitChange"
      >
        <Background :gap="16" />
      </VueFlow>
    </div>

    <!-- 节点配置面板 -->
    <div class="w-64 shrink-0 overflow-y-auto rounded-xl border border-zinc-200/80 p-3 dark:border-zinc-700">
      <div v-if="!selected" class="text-sm text-zinc-400 dark:text-zinc-500">
        点击画布节点编辑配置；拖拽节点连接桩可连线。start → … → end 为执行顺序。
      </div>
      <div v-else class="flex flex-col gap-3">
        <div class="flex items-center justify-between">
          <span class="text-sm font-semibold text-zinc-800 dark:text-zinc-100">{{ typeLabel(selected.kind) }} 节点</span>
          <button
            v-if="selected.kind !== 'start' && selected.kind !== 'end'"
            class="rounded-lg p-1 text-zinc-400 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950/40"
            title="删除节点"
            @click="removeSelected"
          >
            <Trash2 class="h-4 w-4" />
          </button>
        </div>

        <template v-if="selected.kind === 'llm'">
          <AppFormItem label="提示词模板">
            <AppTextarea v-model="selected.prompt" :rows="5" placeholder="可用 {{input}} 引用输入，{{节点id}} 引用上游输出" @update:model-value="syncLabel" />
          </AppFormItem>
        </template>

        <template v-else-if="selected.kind === 'http'">
          <AppFormItem label="方法">
            <AppSelect v-model="selected.method" :options="methodOptions" @update:model-value="syncLabel" />
          </AppFormItem>
          <AppFormItem label="URL">
            <AppInput v-model="selected.url" placeholder="https://… 可含 {{input}}" @update:model-value="syncLabel" />
          </AppFormItem>
          <AppFormItem label="请求体模板">
            <AppTextarea v-model="selected.body" :rows="3" placeholder="可选，JSON 或文本，可含 {{节点id}}" @update:model-value="emitChange" />
          </AppFormItem>
        </template>

        <template v-else-if="selected.kind === 'search'">
          <AppFormItem label="搜索词模板">
            <AppTextarea v-model="selected.query" :rows="3" placeholder="可用 {{input}} 引用输入，{{节点id}} 引用上游输出" @update:model-value="syncLabel" />
          </AppFormItem>
          <AppFormItem label="结果条数">
            <AppInput :model-value="String(selected.topK ?? 3)" type="number" placeholder="默认 3" @update:model-value="setTopK" />
          </AppFormItem>
        </template>

        <template v-else>
          <div class="text-xs text-zinc-400">起点/终点节点无需配置。</div>
        </template>

        <div class="text-xs text-zinc-400">节点 ID：<code>{{ selectedId }}</code></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * WorkflowCanvas — 工作流可视化编排画布（P3-1）
 *
 * 双向绑定 definition JSON（{nodes:[{id,type,data,position}], edges:[{source,target}]}）。
 * 只换编辑器不换引擎：序列化结果即 FastAPI engine 消费的 definition 契约，
 * 旧 JSON 定义可直接载入。节点位置存进 node 一并持久化，engine 忽略多余字段。
 */
import { ref, watch, nextTick } from 'vue'
import { VueFlow, type Connection, addEdge } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Plus, Trash2 } from 'lucide-vue-next'
import { AppButton, AppFormItem, AppInput, AppTextarea, AppSelect } from '@/components/ui'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'

const props = defineProps<{ modelValue?: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()

type NodeKind = 'start' | 'llm' | 'http' | 'search' | 'end'
interface NodeData {
  kind: NodeKind
  prompt?: string
  method?: string
  url?: string
  body?: string
  query?: string
  topK?: number
}
// Vue Flow 的 Node/Edge 类型极深，会触发 TS2589；画布数组用 any 持有，
// 类型安全集中在 NodeData（配置面板）这一真正需要约束的部分。
/* eslint-disable @typescript-eslint/no-explicit-any */
interface FlowNode { id: string; type?: string; position: { x: number; y: number }; data: NodeData; label?: string }
type FlowEdge = { id: string; source: string; target: string }

const nodes = ref<any[]>([])
const edges = ref<any[]>([])
// selected 直接持有节点的 data 对象（与 nodes 中同一引用，v-model 原地修改即生效）
const selected = ref<NodeData | null>(null)
const selectedId = ref<string>('')

const methodOptions = [
  { label: 'GET', value: 'GET' },
  { label: 'POST', value: 'POST' }
]

function typeLabel(k: NodeKind): string {
  return { start: '起点', llm: 'LLM', http: 'HTTP', search: '搜索', end: '终点' }[k]
}

/** 节点展示标签：类型 + 配置摘要 */
function labelOf(d: NodeData): string {
  if (d.kind === 'llm') return `LLM: ${(d.prompt || '').slice(0, 18) || '(未配置)'}`
  if (d.kind === 'http') return `HTTP ${d.method || 'GET'}: ${(d.url || '').slice(0, 16) || '(未配置)'}`
  if (d.kind === 'search') return `搜索: ${(d.query || '').slice(0, 16) || '(未配置)'}`
  return typeLabel(d.kind)
}

let idSeq = 1
function nextId(kind: NodeKind): string {
  return `${kind}_${idSeq++}`
}

function addNode(kind: 'llm' | 'http' | 'search') {
  const id = nextId(kind)
  const data: NodeData =
    kind === 'llm' ? { kind, prompt: '' }
    : kind === 'search' ? { kind, query: '{{input}}', topK: 3 }
    : { kind, method: 'GET', url: '', body: '' }
  nodes.value = [
    ...nodes.value,
    { id, type: 'default', position: { x: 240, y: 60 + nodes.value.length * 80 }, data, label: labelOf(data) }
  ]
  emitChange()
}

function onConnect(conn: Connection) {
  edges.value = addEdge(conn, edges.value)
  emitChange()
}

function onNodeClick(e: { node: { id: string; data?: NodeData } }) {
  selected.value = e.node.data ?? null
  selectedId.value = e.node.id
}

/** 配置变更后刷新选中节点的标签并回写 */
function syncLabel() {
  const node = nodes.value.find((n) => n.id === selectedId.value)
  if (node && node.data) node.label = labelOf(node.data)
  emitChange()
}

/** 搜索节点结果条数：AppInput 只吐字符串，这里收口为 1~10 的整数回写 */
function setTopK(v: string) {
  if (!selected.value) return
  const n = Math.round(Number(v))
  selected.value.topK = Number.isFinite(n) ? Math.min(Math.max(n, 1), 10) : 3
  emitChange()
}

function removeSelected() {
  const id = selectedId.value
  if (!id) return
  nodes.value = nodes.value.filter((n) => n.id !== id)
  edges.value = edges.value.filter((e) => e.source !== id && e.target !== id)
  selected.value = null
  selectedId.value = ''
  emitChange()
}

/** 默认空画布：start → end 两个固定节点 */
function defaultGraph(): { nodes: FlowNode[]; edges: FlowEdge[] } {
  return {
    nodes: [
      { id: 'start', type: 'input', position: { x: 40, y: 40 }, data: { kind: 'start' }, label: '起点' },
      { id: 'end', type: 'output', position: { x: 40, y: 260 }, data: { kind: 'end' }, label: '终点' }
    ],
    edges: []
  }
}

/** 从 definition JSON 载入画布；非法/空则用默认图 */
function loadFromDefinition(def: string) {
  if (!def || !def.trim()) {
    const g = defaultGraph()
    nodes.value = g.nodes
    edges.value = g.edges
    return
  }
  try {
    const spec = JSON.parse(def)
    const ns: FlowNode[] = (spec.nodes || []).map((n: Record<string, unknown>, i: number): FlowNode => {
      const data: NodeData = (n.data as NodeData) || { kind: n.type as NodeKind }
      data.kind = (n.type as NodeKind) ?? data.kind
      const pos = (n.position as { x: number; y: number }) || { x: 240, y: 60 + i * 80 }
      const vfType = data.kind === 'start' ? 'input' : data.kind === 'end' ? 'output' : 'default'
      return { id: String(n.id), type: vfType, position: pos, data, label: labelOf(data) }
    })
    edges.value = (spec.edges || []).map((e: Record<string, unknown>, i: number) => ({
      id: `e${i}`,
      source: String(e.source),
      target: String(e.target)
    }))
    nodes.value = ns.length ? ns : defaultGraph().nodes
    // idSeq 推进到已有最大值之后，避免新增节点 ID 冲突
    idSeq = ns.length + 1
  } catch {
    const g = defaultGraph()
    nodes.value = g.nodes
    edges.value = g.edges
  }
}

/** 序列化为 definition JSON（engine 契约 + 位置持久化） */
function serialize(): string {
  return JSON.stringify({
    nodes: nodes.value.map((n) => ({ id: n.id, type: n.data?.kind, data: n.data, position: n.position })),
    edges: edges.value.map((e) => ({ source: e.source, target: e.target }))
  })
}

// 任意画布变更（增删节点/连线/拖拽/配置）→ 回写 modelValue
let loading = false
function emitChange() {
  if (loading) return
  emit('update:modelValue', serialize())
}

// 外部 definition 变更（打开不同工作流）→ 重载画布
watch(() => props.modelValue, (v) => {
  loading = true
  loadFromDefinition(v ?? '')
  nextTick(() => { loading = false })
}, { immediate: true })
</script>
