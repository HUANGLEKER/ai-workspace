<template>
  <ConversationShell>
    <template #sidebar-header="{ collapsed }">
      <AppSelect
        v-if="!collapsed"
        :model-value="selectedKbId"
        :options="kbOptions"
        numeric
        placeholder="选择知识库"
        class="!w-full"
        @update:model-value="onKbChange"
      />
      <AppButton v-if="!collapsed" variant="primary" :icon="Plus" block :disabled="!selectedKbId" @click="handleCreateSession">新建问答</AppButton>
      <AppButton v-else variant="primary" :icon="Plus" block class="px-0" :disabled="!selectedKbId" @click="handleCreateSession" />
    </template>

    <template #sidebar-list="{ collapsed }">
      <ConversationList
        :sessions="sessions"
        :current-id="currentSession?.id ?? null"
        :collapsed="collapsed"
        :icon="FileSearch"
        :loading="sessionsLoading"
        :show-empty="!sessionsLoading && !!selectedKbId && sessions.length === 0 && !collapsed"
        empty-text="暂无问答会话"
        @select="selectSession"
        @rename="onRename"
        @delete="handleDeleteSession"
      />
    </template>

    <!-- 未选知识库/未选会话的空态 -->
    <div v-if="!selectedKbId" class="flex flex-1 flex-col items-center justify-center gap-3 text-zinc-400 dark:text-zinc-500">
      <BookOpen class="h-14 w-14 text-zinc-200 dark:text-zinc-700" />
      <p class="text-sm">请先在左上角选择一个知识库</p>
    </div>
    <div v-else-if="!currentSession" class="flex flex-1 flex-col items-center justify-center gap-3 text-zinc-400 dark:text-zinc-500">
      <FileSearch class="h-14 w-14 text-zinc-200 dark:text-zinc-700" />
      <p class="text-sm">选择左侧问答，或点击「新建问答」基于「{{ selectedKbName }}」提问</p>
    </div>

    <template v-else>
      <!-- 标题栏 -->
      <div class="flex h-14 shrink-0 items-center justify-between border-b border-line px-5">
        <div class="flex min-w-0 items-center gap-2">
          <span class="truncate text-sm font-semibold text-zinc-800 dark:text-zinc-100">{{ currentSession.title }}</span>
          <span class="flex shrink-0 items-center gap-1 rounded-md bg-zinc-100 px-1.5 py-0.5 text-xs text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400">
            <BookOpen class="h-3 w-3" />{{ selectedKbName }}
          </span>
          <button
            v-if="currentSession.systemPrompt"
            class="flex shrink-0 items-center gap-1 rounded-md bg-zinc-100 px-1.5 py-0.5 text-xs text-zinc-500 transition-all duration-200 ease-out hover:bg-zinc-200 hover:text-zinc-700 dark:bg-zinc-800 dark:text-zinc-400 dark:hover:bg-zinc-700 dark:hover:text-zinc-200"
            :title="'系统提示词：' + currentSession.systemPrompt + '（点击移除）'"
            @click="clearSystemPrompt"
          >
            <Sparkles class="h-3 w-3" />提示词<X class="h-3 w-3" />
          </button>
        </div>
        <div class="flex items-center gap-1">
          <AppButton v-if="artifact.artifacts.value.length && !artifact.open.value" variant="ghost" size="sm" :icon="LayoutPanelLeft" @click="artifact.show(artifact.artifacts.value)">Artifact</AppButton>
          <AppButton variant="ghost" size="sm" :icon="Trash2" @click="clearMessages">清空</AppButton>
        </div>
      </div>

      <ConversationMessages
        ref="messagesRef"
        :messages="messages"
        :streaming="streaming"
        :caret-fading="caretFading"
        :no-animate-idx="noAnimateIdx"
        :stream-display="streamDisplay"
        :thinking="thinkingState.thinking.value"
        :phases="thinkingState.phases.value"
        :in-progress="thinkingState.inProgress.value"
        :live-sources="liveSources"
        :display-usage="displayUsage"
        :usage-estimating="usageEstimating"
        :artifact="artifact"
        @copy="copyMessage"
        @regenerate="regenerateMessage"
        @delete="deleteMessage"
      >
        <template #composer>
          <ConversationComposer
            v-model="inputText"
            v-model:selected-model="selectedModel"
            v-model:web-search="webSearch"
            :streaming="streaming"
            :model-options="modelOptions"
            placeholder="输入你的问题，回答将引用文档内容..."
            hint="Enter 提问 · Shift + Enter 换行 · / 提示词"
            model-placeholder="默认模型"
            :picker-open="pickerOpen"
            :picker-query="pickerQuery"
            @send="handleSend"
            @stop="handleStop"
            @prompt-use="handlePromptUse"
          />
        </template>
      </ConversationMessages>
    </template>
  </ConversationShell>
</template>

<script setup lang="ts">
/**
 * 知识库问答页（RAG）
 *
 * 与 AI 对话页对齐的完整能力：会话历史持久化（左侧会话列表 + 多轮上下文）、
 * 流式输出、Markdown/代码实时渲染、Thinking 阶段、Artifact 分屏、Token 用量、
 * 消息复制/重新生成/删除。RAG 特有：每条回答附带可折叠的引用来源卡片（命中高亮）。
 *
 * 布局骨架（会话侧栏 / 消息流 / 输入区）与 Chat 页共用 Conversation* 组件，
 * 本组件保留 RAG 专属的数据 store、知识库选择、流式管线与交互状态。
 */
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { Plus, Trash2, FileSearch, BookOpen, LayoutPanelLeft, Sparkles, X } from 'lucide-vue-next'
import { storeToRefs } from 'pinia'
import type { RagSession, ChatMessage, RagSource, TokenUsage } from '@/types'
import { ragChatStream, clearRagMessages, updateRagSessionPrompt } from '@/api/kb'
import { useRagStore } from '@/stores/rag'
import { AppButton, AppSelect, toast, confirm } from '@/components/ui'
import ConversationShell from '@/components/chat/ConversationShell.vue'
import ConversationList from '@/components/chat/ConversationList.vue'
import ConversationMessages from '@/components/chat/ConversationMessages.vue'
import ConversationComposer from '@/components/chat/ConversationComposer.vue'
import { useStreamingMarkdown } from '@/composables/useStreamingMarkdown'
import { useThinkingPhases } from '@/composables/useThinkingPhases'
import { useArtifactPanel } from '@/composables/useArtifactPanel'

const ragStore = useRagStore()
const { sessions, sessionsLoading, currentSession, messages, selectedModel, modelOptions, selectedKbId, kbOptions, selectedKbName } =
  storeToRefs(ragStore)
const inputText = ref('')
const streaming = ref(false)
const webSearch = ref(false)
const caretFading = ref(false)
const noAnimateIdx = ref(-1)

// 当前流式轮次已下发的引用来源（先于 token 到达），挂在 live 气泡下方
const liveSources = ref<RagSource[]>([])

const messagesRef = ref<InstanceType<typeof ConversationMessages> | null>(null)
const thinkingState = useThinkingPhases()
const artifact = useArtifactPanel()

const usage = ref<TokenUsage | null>(null)
const { text: streamText, display: streamDisplay, append: appendStream, flush: flushStream, reset: resetStream } =
  useStreamingMarkdown((t) => artifact.sync(t))
let streamController: AbortController | null = null
let fadeTimer: ReturnType<typeof setTimeout> | null = null
const CARET_FADE_MS = 300

function scrollToBottom(force = true) {
  return messagesRef.value?.scrollToBottom(force)
}
function scheduleScroll() {
  messagesRef.value?.scheduleScroll()
}

function finalizeStream(finalText: string) {
  const turnSources = liveSources.value
  if (!finalText) {
    caretFading.value = false
    resetStream()
    thinkingState.reset()
    liveSources.value = []
    return
  }
  caretFading.value = true
  fadeTimer = setTimeout(() => {
    fadeTimer = null
    noAnimateIdx.value = messages.value.length
    messages.value.push({ role: 'assistant', content: finalText, sources: turnSources.length ? turnSources : undefined })
    caretFading.value = false
    resetStream()
    thinkingState.reset()
    liveSources.value = []
    scrollToBottom()
  }, CARET_FADE_MS)
}

function cancelFade() {
  if (fadeTimer) {
    clearTimeout(fadeTimer)
    fadeTimer = null
  }
  caretFading.value = false
  resetStream()
  thinkingState.reset()
  liveSources.value = []
  usage.value = null
}

const displayUsage = computed<TokenUsage | null>(() => {
  if (usage.value) return usage.value
  if (streaming.value) {
    return { promptTokens: 0, completionTokens: Math.ceil(streamText.value.length / 4), totalTokens: 0 }
  }
  return null
})
const usageEstimating = computed(() => !usage.value && streaming.value)

onMounted(() => {
  ragStore.loadModels()
  ragStore.loadKbs()
})

function onKbChange(id: unknown) {
  cancelFade()
  artifact.close()
  artifact.clearVersions()
  ragStore.selectKb(id as number | undefined)
}

async function selectSession(session: RagSession) {
  cancelFade()
  artifact.close()
  artifact.clearVersions()
  await ragStore.selectSession(session)
  scrollToBottom()
}

async function handleCreateSession() {
  try {
    const session = await ragStore.createSession()
    selectSession(session)
  } catch {
    toast.error('创建问答失败')
  }
}

async function onRename(session: RagSession, title: string) {
  try {
    await ragStore.renameSessionTitle(session, title)
  } catch {
    toast.error('重命名失败')
  }
}

async function handleDeleteSession(id: number) {
  const ok = await confirm({ title: '删除确认', message: '确定删除该问答会话吗？', confirmText: '确定删除', danger: true })
  if (!ok) return
  try {
    await ragStore.removeSession(id)
    toast.success('删除成功')
  } catch {
    toast.error('删除失败')
  }
}

async function clearMessages() {
  if (!currentSession.value) return
  const ok = await confirm({ title: '清空确认', message: '确定清空当前问答记录吗？', confirmText: '确定清空', danger: true })
  if (!ok) return
  try {
    cancelFade()
    artifact.close()
    await clearRagMessages(currentSession.value.id)
    ragStore.clearMessages()
  } catch {
    toast.error('清空失败')
  }
}

onBeforeUnmount(() => {
  if (fadeTimer) clearTimeout(fadeTimer)
})

// ── 提示词选择器：输入框以 / 开头唤起，键盘交互在 ConversationComposer 内聚 ──
const pickerOpen = computed(() => !!currentSession.value && !streaming.value && inputText.value.startsWith('/'))
const pickerQuery = computed(() => (pickerOpen.value ? inputText.value.slice(1) : ''))

async function handlePromptUse(text: string, asSystem: boolean) {
  if (!asSystem) {
    inputText.value = text
    return
  }
  if (!currentSession.value) return
  try {
    await updateRagSessionPrompt(currentSession.value.id, text)
    currentSession.value.systemPrompt = text
    inputText.value = ''
    toast.success('已设为本会话系统提示词')
  } catch {
    toast.error('设置失败')
  }
}

async function clearSystemPrompt() {
  if (!currentSession.value) return
  try {
    await updateRagSessionPrompt(currentSession.value.id, '')
    currentSession.value.systemPrompt = ''
    toast.success('已移除系统提示词')
  } catch {
    toast.error('移除失败')
  }
}

async function handleSend() {
  const content = inputText.value.trim()
  if (!content || streaming.value || !currentSession.value) return

  noAnimateIdx.value = -1
  if (caretFading.value && streamText.value) {
    if (fadeTimer) { clearTimeout(fadeTimer); fadeTimer = null }
    noAnimateIdx.value = messages.value.length
    messages.value.push({ role: 'assistant', content: streamText.value, sources: liveSources.value.length ? liveSources.value : undefined })
    caretFading.value = false
    resetStream()
  }

  inputText.value = ''
  messages.value.push({ role: 'user', content })
  await scrollToBottom()
  streamReply(content)
}

/** 启动一轮流式问答（发送新问题与「重新生成」共用） */
function streamReply(content: string) {
  if (!currentSession.value) return
  streaming.value = true
  usage.value = null
  liveSources.value = []
  resetStream()
  thinkingState.start()
  artifact.newTurn()
  let firstToken = true
  streamController = new AbortController()

  ragChatStream(
    { sessionId: currentSession.value.id, question: content, model: selectedModel.value || undefined, webSearch: webSearch.value },
    (text) => {
      if (firstToken) {
        firstToken = false
        thinkingState.markGenerating()
      }
      appendStream(text)
      scheduleScroll()
    },
    (sources) => {
      // sources 帧先于 token 到达：点亮检索阶段并挂到 live 气泡
      liveSources.value = sources
      thinkingState.addPhase('retrieve', '检索知识库')
      scheduleScroll()
    },
    () => {
      flushStream()
      const finalText = streamText.value
      streaming.value = false
      streamController = null
      thinkingState.finish()
      artifact.sync(finalText)
      finalizeStream(finalText)
    },
    (err) => {
      streaming.value = false
      streamController = null
      cancelFade()
      toast.error('问答失败：' + err)
    },
    streamController.signal,
    {
      onUsage: (u) => { usage.value = u },
      onTitle: (title) => { if (currentSession.value) ragStore.setSessionTitle(currentSession.value.id, title) },
      onStatus: () => thinkingState.addPhase('websearch', '联网搜索中')
    }
  )
}

function handleStop() {
  streamController?.abort()
}

async function copyMessage(msg: ChatMessage) {
  try {
    await navigator.clipboard.writeText(msg.content)
    toast.success('已复制')
  } catch {
    toast.error('复制失败')
  }
}

function regenerateMessage(idx: number) {
  if (streaming.value || caretFading.value) return
  const target = messages.value[idx]
  if (!target || target.role !== 'assistant') return
  let userIdx = idx - 1
  while (userIdx >= 0 && messages.value[userIdx].role !== 'user') userIdx--
  if (userIdx < 0) return
  const prompt = messages.value[userIdx].content
  messages.value = messages.value.slice(0, userIdx + 1)
  noAnimateIdx.value = -1
  scrollToBottom()
  streamReply(prompt)
}

function deleteMessage(idx: number) {
  messages.value.splice(idx, 1)
  toast.success('已删除')
}
</script>
