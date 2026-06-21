<template>
  <ConversationShell>
    <template #sidebar-header="{ collapsed }">
      <AppButton v-if="!collapsed" variant="primary" :icon="Plus" block @click="handleCreateSession">新建对话</AppButton>
      <AppButton v-else variant="primary" :icon="Plus" block class="px-0" @click="handleCreateSession" />
    </template>

    <template #sidebar-list="{ collapsed }">
      <ConversationList
        :sessions="sessions"
        :current-id="currentSession?.id ?? null"
        :collapsed="collapsed"
        :icon="MessageSquare"
        :loading="sessionsLoading"
        :show-empty="!sessionsLoading && sessions.length === 0 && !collapsed"
        empty-text="暂无对话"
        @select="selectSession"
        @rename="onRename"
        @delete="handleDeleteSession"
      />
    </template>

    <!-- 未选择对话时的空态 -->
    <div v-if="!currentSession" class="flex flex-1 flex-col items-center justify-center gap-3 text-zinc-400 dark:text-zinc-500">
      <MessageSquare class="h-14 w-14 text-zinc-200 dark:text-zinc-700" />
      <p class="text-sm">选择左侧对话，或点击「新建对话」开始</p>
    </div>

    <template v-else>
      <!-- 对话标题栏 -->
      <div class="flex h-14 shrink-0 items-center justify-between border-b border-line px-5">
        <div class="flex min-w-0 items-center gap-2">
          <span class="truncate text-sm font-semibold text-zinc-800 dark:text-zinc-100">{{ currentSession.title }}</span>
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
            placeholder="输入消息..."
            hint="Enter 发送 · Shift + Enter 换行 · / 提示词"
            model-placeholder="选择模型"
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
 * AI 对话页
 *
 * 在原有「会话列表 / 流式消息 / 输入区」基础上集成 Claude 风格高级能力：
 * 1. Thinking 状态展示（useThinkingPhases + ThinkingIndicator）：响应开始先展示
 *    Analyzing / Retrieving / Generating 阶段清单，由流生命周期驱动，不暴露内部 Prompt。
 * 2. Artifact 面板（useArtifactPanel + ArtifactPanel）：检测 Markdown/SQL/HTML/JSON/
 *    Mermaid/PRD/长代码 时自动右侧分屏预览，支持实时更新 / 全屏 / Copy / Download。
 * 3. 消息折叠（ChatMessageItem）：超过 300 行自动 Show More / Show Less。
 * 4. 会话性能（ChatMessageList）：消息超 1000 条启用虚拟滚动，保持 SSE 与自动滚动。
 *
 * 布局骨架（会话侧栏 / 消息流 / 输入区）与 RAG 页共用 Conversation* 组件，
 * 本组件只保留 chat 专属的数据 store、流式管线与交互状态。
 */
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { Plus, Trash2, MessageSquare, LayoutPanelLeft, Sparkles, X } from 'lucide-vue-next'
import { storeToRefs } from 'pinia'
import type { ChatSession, ChatMessage, TokenUsage } from '@/types'
import { sendMessageStream, updateSessionPrompt } from '@/api/chat'
import { useChatStore } from '@/stores/chat'
import { AppButton, toast, confirm } from '@/components/ui'
import ConversationShell from '@/components/chat/ConversationShell.vue'
import ConversationList from '@/components/chat/ConversationList.vue'
import ConversationMessages from '@/components/chat/ConversationMessages.vue'
import ConversationComposer from '@/components/chat/ConversationComposer.vue'
import { useStreamingMarkdown } from '@/composables/useStreamingMarkdown'
import { useThinkingPhases } from '@/composables/useThinkingPhases'
import { useArtifactPanel } from '@/composables/useArtifactPanel'

// 数据状态收敛在 chat store；本组件只保留流式管线与交互状态
const chatStore = useChatStore()
const { sessions, sessionsLoading, currentSession, messages, selectedModel, modelOptions } = storeToRefs(chatStore)
const inputText = ref('')
const streaming = ref(false)
const webSearch = ref(false)
const caretFading = ref(false)
const noAnimateIdx = ref(-1)

const messagesRef = ref<InstanceType<typeof ConversationMessages> | null>(null)
const thinkingState = useThinkingPhases()
const artifact = useArtifactPanel()

const usage = ref<TokenUsage | null>(null)
// 流式 Markdown：display 每帧至多刷新一次；onUpdate 同步驱动 Artifact 实时预览
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
  if (!finalText) {
    caretFading.value = false
    resetStream()
    thinkingState.reset()
    return
  }
  caretFading.value = true
  fadeTimer = setTimeout(() => {
    fadeTimer = null
    noAnimateIdx.value = messages.value.length
    messages.value.push({ role: 'assistant', content: finalText })
    caretFading.value = false
    resetStream()
    thinkingState.reset()
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
  chatStore.loadModels()
  chatStore.loadSessions()
})

async function selectSession(session: ChatSession) {
  cancelFade()
  artifact.close()
  artifact.clearVersions() // 切换会话清空 Artifact 版本链，避免跨会话串味
  await chatStore.selectSession(session)
  scrollToBottom()
}

async function handleCreateSession() {
  try {
    const session = await chatStore.createSession()
    selectSession(session)
  } catch {
    toast.error('创建对话失败')
  }
}

async function onRename(session: ChatSession, title: string) {
  try {
    await chatStore.renameSessionTitle(session, title) // 乐观更新，失败自动回滚
  } catch {
    toast.error('重命名失败')
  }
}

async function handleDeleteSession(id: number) {
  const ok = await confirm({ title: '删除确认', message: '确定删除该对话吗？', confirmText: '确定删除', danger: true })
  if (!ok) return
  try {
    await chatStore.removeSession(id)
    toast.success('删除成功')
  } catch {
    toast.error('删除失败')
  }
}

async function clearMessages() {
  const ok = await confirm({ title: '清空确认', message: '确定清空当前对话记录吗？', confirmText: '确定清空', danger: true })
  if (ok) {
    cancelFade()
    artifact.close()
    chatStore.clearMessages()
  }
}

onBeforeUnmount(() => {
  if (fadeTimer) clearTimeout(fadeTimer)
})

// ── 提示词选择器（P1-5）：输入框以 / 开头唤起，键盘交互在 ConversationComposer 内聚 ──
const pickerOpen = computed(() => !!currentSession.value && !streaming.value && inputText.value.startsWith('/'))
const pickerQuery = computed(() => (pickerOpen.value ? inputText.value.slice(1) : ''))

async function handlePromptUse(text: string, asSystem: boolean) {
  if (!asSystem) {
    inputText.value = text
    return
  }
  if (!currentSession.value) return
  try {
    await updateSessionPrompt(currentSession.value.id, text)
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
    await updateSessionPrompt(currentSession.value.id, '')
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
    messages.value.push({ role: 'assistant', content: streamText.value })
    caretFading.value = false
    resetStream()
  }

  inputText.value = ''
  messages.value.push({ role: 'user', content })
  await scrollToBottom()
  streamReply(content)
}

/** 启动一轮流式回复（发送新消息与「重新生成」共用） */
function streamReply(content: string) {
  if (!currentSession.value) return
  streaming.value = true
  usage.value = null
  resetStream()
  thinkingState.start() // Feature 1：先展示思考阶段
  artifact.newTurn()
  let firstToken = true
  streamController = new AbortController()

  sendMessageStream(
    currentSession.value.id,
    content,
    (text) => {
      if (firstToken) {
        firstToken = false
        thinkingState.markGenerating() // 首 token 到达：点亮「Generating」
      }
      appendStream(text)
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
      toast.error('发送失败：' + err)
    },
    streamController.signal,
    (u) => {
      usage.value = u
    },
    (title) => {
      // 后端用首条消息自动生成了标题：同步更新侧边栏与标题栏
      if (currentSession.value) chatStore.setSessionTitle(currentSession.value.id, title)
    },
    {
      webSearch: webSearch.value,
      // 联网搜索状态帧：点亮 thinking 指示器的「联网搜索中」阶段（不进入答案正文）
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
