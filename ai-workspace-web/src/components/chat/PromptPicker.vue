<template>
  <!-- 提示词选择浮层：输入框键入 / 唤起，悬浮于输入区上方 -->
  <div
    v-if="open"
    class="absolute bottom-full left-0 right-0 z-20 mb-2 max-h-72 overflow-y-auto rounded-2xl border border-zinc-200/80 bg-white p-1.5 shadow-lg dark:border-zinc-700 dark:bg-zinc-800"
  >
    <div class="px-2.5 py-1.5 text-xs text-zinc-400 dark:text-zinc-500">
      提示词中心 · ↑↓ 选择 · Enter 插入 · Esc 关闭
    </div>
    <div v-if="loading" class="px-2.5 py-3 text-sm text-zinc-400">加载中…</div>
    <div v-else-if="!filtered.length" class="px-2.5 py-3 text-sm text-zinc-400">
      没有匹配的提示词，可在「提示词中心」创建
    </div>
    <div
      v-for="(p, i) in filtered"
      :key="p.id"
      class="group flex cursor-pointer items-center gap-2 rounded-xl px-2.5 py-2 transition-all duration-200 ease-out"
      :class="i === activeIdx ? 'bg-zinc-100 dark:bg-zinc-700/60' : 'hover:bg-zinc-50 dark:hover:bg-zinc-700/40'"
      @click="choose(p, false)"
      @mouseenter="activeIdx = i"
    >
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-2">
          <span class="truncate text-sm font-medium text-zinc-800 dark:text-zinc-100">{{ p.title }}</span>
          <span v-if="p.category" class="shrink-0 rounded-md bg-zinc-100 px-1.5 py-0.5 text-xs text-zinc-500 dark:bg-zinc-700 dark:text-zinc-400">{{ p.category }}</span>
        </div>
        <div class="truncate text-xs text-zinc-400 dark:text-zinc-500">{{ p.description || p.content }}</div>
      </div>
      <button
        class="shrink-0 rounded-lg border border-zinc-200 px-2 py-1 text-xs text-zinc-500 opacity-0 transition-all duration-200 ease-out hover:bg-zinc-100 hover:text-zinc-800 group-hover:opacity-100 dark:border-zinc-600 dark:text-zinc-400 dark:hover:bg-zinc-700 dark:hover:text-zinc-100"
        title="作为本会话的系统提示词，对后续每条消息生效"
        @click.stop="choose(p, true)"
      >
        设为系统提示词
      </button>
    </div>
  </div>

  <!-- 变量填空对话框：提示词含 {{变量}} 占位符时弹出 -->
  <AppDialog v-model="fillOpen" :title="`填写变量 · ${pendingPrompt?.title ?? ''}`" width="480px">
    <div class="flex flex-col gap-3">
      <AppFormItem v-for="v in pendingVars" :key="v" :label="v">
        <AppInput v-model="varValues[v]" :placeholder="`{{${v}}}`" />
      </AppFormItem>
    </div>
    <template #footer>
      <AppButton variant="secondary" @click="fillOpen = false">取消</AppButton>
      <AppButton variant="primary" @click="confirmFill">确定</AppButton>
    </template>
  </AppDialog>
</template>

<script setup lang="ts">
/**
 * PromptPicker — 提示词中心与 Chat 的执行层打通（P1-5）
 *
 * 两条使用路径：
 * 1. 点击条目 / Enter：内容注入输入框（含 {{变量}} 时先弹填空对话框）
 * 2. 「设为系统提示词」：绑定到当前会话（同样支持变量填空），后端拼为首条 system 消息
 *
 * 键盘交互由父组件转发（输入框保持焦点）：moveActive / chooseActive 经 defineExpose 暴露。
 */
import { ref, computed, watch } from 'vue'
import type { Prompt } from '@/api/prompt'
import { listPrompts } from '@/api/prompt'
import { AppDialog, AppFormItem, AppInput, AppButton } from '@/components/ui'
import { extractVars, fillVars } from '@/utils/promptVars'

const props = defineProps<{
  open: boolean
  /** 输入框 "/" 之后的过滤关键字 */
  query: string
}>()

const emit = defineEmits<{
  /** 最终文本产出：asSystem=false 注入输入框，true 绑定为会话系统提示词 */
  (e: 'use', text: string, asSystem: boolean): void
}>()

const prompts = ref<Prompt[]>([])
const loading = ref(false)
const loaded = ref(false)
const activeIdx = ref(0)

// 首次打开时拉取一次；提示词在会话期内变化不频繁，不做实时刷新
watch(
  () => props.open,
  async (open) => {
    if (!open || loaded.value) return
    loading.value = true
    try {
      prompts.value = await listPrompts()
      loaded.value = true
    } catch {
      prompts.value = []
    } finally {
      loading.value = false
    }
  }
)

const filtered = computed(() => {
  const q = props.query.trim().toLowerCase()
  if (!q) return prompts.value
  return prompts.value.filter(
    (p) =>
      p.title.toLowerCase().includes(q) ||
      (p.category ?? '').toLowerCase().includes(q) ||
      (p.description ?? '').toLowerCase().includes(q)
  )
})

watch(filtered, () => {
  activeIdx.value = 0
})

// ── 变量填空 ──────────────────────────────────────────────
const fillOpen = ref(false)
const pendingPrompt = ref<Prompt | null>(null)
const pendingAsSystem = ref(false)
const pendingVars = ref<string[]>([])
const varValues = ref<Record<string, string>>({})

function choose(p: Prompt, asSystem: boolean) {
  const vars = extractVars(p.content)
  if (!vars.length) {
    emit('use', p.content, asSystem)
    return
  }
  pendingPrompt.value = p
  pendingAsSystem.value = asSystem
  pendingVars.value = vars
  varValues.value = Object.fromEntries(vars.map((v) => [v, '']))
  fillOpen.value = true
}

function confirmFill() {
  if (!pendingPrompt.value) return
  emit('use', fillVars(pendingPrompt.value.content, varValues.value), pendingAsSystem.value)
  fillOpen.value = false
  pendingPrompt.value = null
}

// ── 父组件键盘转发 ────────────────────────────────────────
function moveActive(delta: number) {
  const n = filtered.value.length
  if (!n) return
  activeIdx.value = (activeIdx.value + delta + n) % n
}

function chooseActive() {
  const p = filtered.value[activeIdx.value]
  if (p) choose(p, false)
}

defineExpose({ moveActive, chooseActive })
</script>
