<template>
  <div class="flex flex-1 flex-col overflow-hidden bg-white dark:bg-zinc-900">
    <!-- 顶部：知识库选择 -->
    <div class="flex h-14 shrink-0 items-center gap-3.5 border-b border-zinc-200/80 px-5 dark:border-zinc-800">
      <div class="mr-auto flex items-center gap-2 text-sm font-semibold text-zinc-800 dark:text-zinc-100">
        <FileSearch class="h-4.5 w-4.5" />
        知识库问答
      </div>
      <AppSelect
        v-model="selectedKbId"
        :options="kbOptions"
        numeric
        placeholder="选择知识库"
        class="!w-60 max-w-60"
        @change="resetConversation"
      />
      <AppButton variant="ghost" size="sm" :icon="Trash2" :disabled="streaming" @click="resetConversation">清空</AppButton>
    </div>

    <!-- 问答区 -->
    <div ref="containerRef" class="flex flex-1 flex-col gap-6 overflow-y-auto p-5">
      <div v-if="!selectedKbId" class="flex flex-1 flex-col items-center justify-center gap-3 text-zinc-400 dark:text-zinc-500">
        <BookOpen class="h-12 w-12 text-zinc-200 dark:text-zinc-700" />
        <p class="text-sm">请先在上方选择一个知识库</p>
      </div>

      <div v-else-if="turns.length === 0 && !streaming" class="flex flex-1 flex-col items-center justify-center gap-3 text-zinc-400 dark:text-zinc-500">
        <FileSearch class="h-12 w-12 text-zinc-200 dark:text-zinc-700" />
        <p class="text-sm">基于「{{ selectedKbName }}」提问，回答将引用文档内容</p>
      </div>

      <div v-for="(turn, idx) in turns" :key="idx" class="flex flex-col gap-3">
        <!-- 问题 -->
        <div class="flex flex-row-reverse items-start gap-3">
          <AppAvatar :icon="User" />
          <div class="max-w-[72%] break-words rounded-2xl bg-zinc-100 px-4 py-2.5 text-sm leading-relaxed text-zinc-900 dark:bg-zinc-700 dark:text-zinc-100">
            {{ turn.question }}
          </div>
        </div>

        <!-- 回答 -->
        <div class="flex items-start gap-3">
          <AppAvatar :icon="Bot" variant="dark" />
          <div class="max-w-[80%] break-words rounded-2xl bg-zinc-100/50 px-4 py-3 text-sm leading-relaxed text-zinc-800 dark:bg-zinc-800 dark:text-zinc-100">
            <MarkdownView :content="turn.answer" />
            <span v-if="streaming && idx === turns.length - 1 && !turn.answer" class="inline-block animate-pulse font-bold">▋</span>

            <!-- 引用来源 -->
            <div v-if="turn.sources.length" class="mt-3 border-t border-dashed border-zinc-200/80 pt-2.5 dark:border-zinc-700">
              <div class="mb-1.5 flex items-center gap-1.5 text-xs text-zinc-500 dark:text-zinc-400">
                <FileText class="h-3.5 w-3.5" />
                引用来源（{{ turn.sources.length }}）
              </div>
              <CollapsibleRoot v-for="(src, sIdx) in turn.sources" :key="sIdx" class="mb-1 last:mb-0">
                <CollapsibleTrigger
                  class="flex w-full items-center gap-2 rounded-xl border px-3 py-2 text-left text-sm transition-all duration-200 ease-out [&[data-state=open]>svg]:rotate-180"
                  :class="src.cited
                    ? 'border-emerald-300 bg-emerald-50 text-zinc-800 hover:bg-emerald-100/70 dark:border-emerald-700/60 dark:bg-emerald-900/20 dark:text-zinc-100'
                    : 'border-zinc-200/80 bg-white text-zinc-800 hover:bg-zinc-50 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100 dark:hover:bg-zinc-700'"
                >
                  <span class="shrink-0 text-xs text-zinc-400">[{{ sIdx + 1 }}]</span>
                  <span class="flex-1 truncate">{{ src.file_name || '未知文档' }}</span>
                  <AppTag v-if="src.cited" variant="success">已引用</AppTag>
                  <AppTag v-if="src.rerank_score != null" variant="warning">精排 {{ (src.rerank_score * 100).toFixed(0) }}</AppTag>
                  <AppTag variant="info">相关度 {{ (src.score * 100).toFixed(0) }}%</AppTag>
                  <ChevronDown class="h-4 w-4 shrink-0 text-zinc-400 transition-all duration-200 ease-out" />
                </CollapsibleTrigger>
                <CollapsibleContent
                  class="mt-1 max-h-[200px] overflow-y-auto whitespace-pre-wrap rounded-xl bg-zinc-50 px-3 py-2 text-sm leading-relaxed text-zinc-500 dark:bg-zinc-800/60 dark:text-zinc-400"
                >
                  {{ src.content }}
                </CollapsibleContent>
              </CollapsibleRoot>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="flex shrink-0 items-end gap-2.5 border-t border-zinc-200/80 px-4 py-3 dark:border-zinc-800">
      <AppTextarea
        v-model="question"
        :rows="1"
        auto-grow
        :disabled="!selectedKbId"
        placeholder="输入你的问题，Enter 提问，Shift + Enter 换行"
        @keydown="onInputKeydown"
      />
      <button
        v-if="streaming"
        class="flex h-12 shrink-0 items-center gap-1.5 rounded-xl bg-red-600 px-5 text-sm text-white transition-all duration-200 ease-out hover:bg-red-500"
        @click="handleStop"
      >
        <CircleStop class="h-4 w-4" />
        停止
      </button>
      <button
        v-else
        class="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-zinc-900 text-white transition-all duration-200 ease-out hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200"
        :class="question.trim() && selectedKbId ? '' : 'pointer-events-none opacity-50'"
        @click="handleAsk"
      >
        <Send class="h-4 w-4" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 知识库问答页（RAG）
 *
 * 1. 顶部选择知识库，切换时重置会话
 * 2. 以 QaTurn（问题 + 答案 + 来源）为单位展示问答历史
 * 3. 流式接收 LLM 回答，sources 引用来源用 Radix Collapsible 折叠展示
 *
 * 每轮问答独立持有一个 QaTurn 引用，流式 token 直接追加到该对象。
 */
import { CollapsibleRoot, CollapsibleTrigger, CollapsibleContent } from 'radix-vue'
import {
  FileSearch, BookOpen, FileText, Trash2, Bot, User, Send, CircleStop, ChevronDown
} from 'lucide-vue-next'
import type { KnowledgeBase, RagSource } from '@/types'
import { listKnowledgeBases, ragChatStream } from '@/api/kb'
import { toast } from '@/components/ui'
import { useChatScroll } from '@/composables/useChatScroll'
import { useStreamingMarkdown } from '@/composables/useStreamingMarkdown'

/** 单轮问答数据结构，流式输出期间 answer 逐步填充 */
interface QaTurn {
  question: string
  answer: string
  sources: RagSource[]
}

const knowledgeBases = ref<KnowledgeBase[]>([])
const selectedKbId = ref<number>()
const question = ref('')
const streaming = ref(false)
const turns = ref<QaTurn[]>([])
const { containerRef, scrollToBottom, scheduleScroll } = useChatScroll()
let streamController: AbortController | null = null

const kbOptions = computed(() => knowledgeBases.value.map((kb) => ({ label: kb.kbName, value: kb.id })))
const selectedKbName = computed(
  () => knowledgeBases.value.find((kb) => kb.id === selectedKbId.value)?.kbName ?? ''
)

onMounted(loadKbs)

async function loadKbs() {
  try {
    knowledgeBases.value = await listKnowledgeBases()
  } catch {
    knowledgeBases.value = []
  }
}

function resetConversation() {
  if (streaming.value) return
  turns.value = []
  question.value = ''
}

function onInputKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    handleAsk()
  }
}

function handleAsk() {
  const q = question.value.trim()
  if (!q || streaming.value || !selectedKbId.value) return

  question.value = ''
  const turn: QaTurn = { question: q, answer: '', sources: [] }
  turns.value.push(turn)
  scrollToBottom()

  streaming.value = true
  streamController = new AbortController()
  // 节流渲染：token 高频到达时每帧至多把累积文本同步进 turn.answer 一次
  const stream = useStreamingMarkdown((text) => { turn.answer = text })

  ragChatStream(
    { kbId: selectedKbId.value, question: q, sessionId: `rag-${selectedKbId.value}` },
    (text) => {
      stream.append(text)
      scheduleScroll()
    },
    (sources) => {
      turn.sources = sources
    },
    () => {
      stream.flush()
      streaming.value = false
      streamController = null
      scrollToBottom()
    },
    (err) => {
      stream.flush()
      streaming.value = false
      streamController = null
      // 若连一个 token 都未收到则移除占位轮次，避免展示空气泡
      if (!turn.answer) turns.value.pop()
      toast.error('问答失败：' + err)
    },
    streamController.signal
  )
}

function handleStop() {
  streamController?.abort()
}
</script>
