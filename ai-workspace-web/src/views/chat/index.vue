<template>
  <div class="flex flex-1 overflow-hidden bg-white dark:bg-zinc-900">
    <!-- 会话列表侧边栏 -->
    <div
      class="relative flex shrink-0 flex-col border-r border-zinc-200/80 bg-zinc-50 transition-all duration-200 ease-out dark:border-zinc-800 dark:bg-zinc-900"
      :class="collapsed ? 'w-[72px]' : 'w-[240px]'"
    >
      <div class="border-b border-zinc-200/80 p-3 dark:border-zinc-800">
        <AppButton v-if="!collapsed" variant="primary" :icon="Plus" block @click="handleCreateSession">新建对话</AppButton>
        <AppButton v-else variant="primary" :icon="Plus" block class="px-0" @click="handleCreateSession"></AppButton>
      </div>

      <div class="relative flex-1 overflow-y-auto p-2">
        <div
          v-for="session in sessions"
          :key="session.id"
          class="group mb-0.5 flex cursor-pointer items-center gap-2 rounded-xl px-3 py-2.5 text-sm transition-all duration-200 ease-out"
          :class="[
            currentSession?.id === session.id
              ? 'bg-zinc-200/80 text-zinc-900 font-medium dark:bg-zinc-800 dark:text-zinc-100'
              : 'text-zinc-500 hover:bg-zinc-100/50 hover:text-zinc-800 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100',
            collapsed ? 'justify-center px-0' : ''
          ]"
          @click="selectSession(session)"
          @dblclick="!collapsed && startRename(session)"
        >
          <AppTooltip v-if="collapsed" :content="session.title" side="right">
            <MessageSquare class="h-4 w-4 shrink-0" />
          </AppTooltip>
          <MessageSquare v-else class="h-4 w-4 shrink-0" />

          <!-- 重命名输入态 -->
          <input
            v-if="!collapsed && renamingId === session.id"
            ref="renameInputRef"
            v-model="renameText"
            class="min-w-0 flex-1 rounded-md border border-zinc-300 bg-white px-1.5 py-0.5 text-sm text-zinc-900 outline-none focus:border-zinc-400 dark:border-zinc-600 dark:bg-zinc-800 dark:text-zinc-100"
            @click.stop
            @keydown.enter.prevent="commitRename(session)"
            @keydown.esc.prevent="cancelRename"
            @blur="commitRename(session)"
          />
          <span v-else-if="!collapsed" class="flex-1 truncate">{{ session.title }}</span>

          <template v-if="!collapsed && renamingId !== session.id">
            <button
              class="shrink-0 rounded-lg p-0.5 opacity-0 transition-all duration-200 ease-out group-hover:opacity-100"
              :class="currentSession?.id === session.id ? 'hover:bg-zinc-700 dark:hover:bg-zinc-300' : 'hover:bg-zinc-200 dark:hover:bg-zinc-700'"
              @click.stop="startRename(session)"
            >
              <Pencil class="h-3.5 w-3.5" />
            </button>
            <button
              class="shrink-0 rounded-lg p-0.5 opacity-0 transition-all duration-200 ease-out group-hover:opacity-100"
              :class="currentSession?.id === session.id ? 'hover:bg-zinc-700 dark:hover:bg-zinc-300' : 'hover:bg-zinc-200 dark:hover:bg-zinc-700'"
              @click.stop="handleDeleteSession(session.id)"
            >
              <Trash2 class="h-3.5 w-3.5" />
            </button>
          </template>
        </div>

        <AppEmpty v-if="!sessionsLoading && sessions.length === 0 && !collapsed" description="暂无对话" />
        <AppLoading v-if="sessionsLoading" overlay />
      </div>

      <!-- 折叠按钮 -->
      <button
        class="flex h-11 shrink-0 items-center justify-center border-t border-zinc-200/80 text-zinc-500 transition-all duration-200 ease-out hover:bg-zinc-100/50 hover:text-zinc-800 dark:border-zinc-800 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100"
        @click="collapsed = !collapsed"
      >
        <PanelLeft class="h-4 w-4" />
      </button>
    </div>

    <!-- 对话主区域 -->
    <div class="flex flex-1 flex-col overflow-hidden">
      <!-- 未选择对话时的空态 -->
      <div v-if="!currentSession" class="flex flex-1 flex-col items-center justify-center gap-3 text-zinc-400 dark:text-zinc-500">
        <MessageSquare class="h-14 w-14 text-zinc-200 dark:text-zinc-700" />
        <p class="text-sm">选择左侧对话，或点击「新建对话」开始</p>
      </div>

      <template v-else>
        <!-- 对话标题栏 -->
        <div class="flex h-14 shrink-0 items-center justify-between border-b border-zinc-200/80 px-5 dark:border-zinc-800">
          <span class="truncate text-sm font-semibold text-zinc-800 dark:text-zinc-100">{{ currentSession.title }}</span>
          <div class="flex items-center gap-1">
            <AppButton v-if="artifact.artifacts.value.length && !artifact.open.value" variant="ghost" size="sm" :icon="LayoutPanelLeft" @click="artifact.show(artifact.artifacts.value)">Artifact</AppButton>
            <AppButton variant="ghost" size="sm" :icon="Trash2" @click="clearMessages">清空</AppButton>
          </div>
        </div>

        <!-- 左侧聊天区 + 右侧 Artifact 预览区（可拖拽分隔，Feature 2） -->
        <SplitterGroup direction="horizontal" class="flex flex-1 overflow-hidden">
          <SplitterPanel :default-size="artifact.open.value ? 58 : 100" :min-size="34" class="flex flex-col overflow-hidden">
            <!-- 消息区：relative 容器承载滚动区 + 浮动「回到底部」按钮 -->
            <div class="relative flex flex-1 overflow-hidden">
              <ChatMessageList
                ref="listRef"
                :messages="messages"
                :busy="streaming || caretFading"
                :no-animate-idx="noAnimateIdx"
                :streaming="streaming"
                :caret-fading="caretFading"
                :thinking="thinkingState.thinking.value"
                :stream-display="streamDisplay"
                :phases="thinkingState.phases.value"
                :in-progress="thinkingState.inProgress.value"
                @copy="copyMessage"
                @regenerate="regenerateMessage"
                @delete="deleteMessage"
                @open-artifact="(a) => artifact.show(a)"
              />

              <!-- 浮动「回到底部」按钮 -->
              <Transition name="msg">
                <button
                  v-if="listRef && !listRef.pinned"
                  class="absolute bottom-4 left-1/2 flex h-9 w-9 -translate-x-1/2 items-center justify-center rounded-full border border-zinc-200/80 bg-white text-zinc-600 shadow-md transition-all duration-200 ease-out hover:text-zinc-900 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 dark:hover:text-white"
                  @click="listRef?.scrollToBottom()"
                >
                  <ArrowDown class="h-4 w-4" />
                </button>
              </Transition>
            </div>

            <!-- Token 用量统计条 -->
            <div
              v-if="displayUsage"
              class="flex shrink-0 items-center gap-3 border-t border-zinc-200/80 px-5 py-1.5 text-xs text-zinc-400 dark:border-zinc-800 dark:text-zinc-500"
            >
              <span>Prompt: <span class="font-medium text-zinc-600 dark:text-zinc-300">{{ displayUsage.promptTokens }}</span></span>
              <span>Completion: <span class="font-medium text-zinc-600 dark:text-zinc-300">{{ usageEstimating ? '~' : '' }}{{ displayUsage.completionTokens }}</span></span>
              <span>Total: <span class="font-medium text-zinc-600 dark:text-zinc-300">{{ usageEstimating ? '~' : displayUsage.totalTokens }}</span></span>
            </div>

            <!-- 输入区域 -->
            <div class="shrink-0 border-t border-zinc-200/80 bg-white px-4 py-3 dark:border-zinc-800 dark:bg-zinc-900">
              <div class="mb-2 flex items-center justify-between">
                <AppSelect v-model="selectedModel" :options="modelOptions" placeholder="选择模型" class="!w-44 max-w-44" />
                <span class="text-xs text-zinc-400 dark:text-zinc-500">Enter 发送 · Shift + Enter 换行</span>
              </div>
              <div class="flex items-end gap-2.5">
                <AppTextarea v-model="inputText" :rows="1" auto-grow placeholder="输入消息..." @keydown="onInputKeydown" />
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
                  :class="inputText.trim() ? '' : 'pointer-events-none opacity-50'"
                  @click="handleSend"
                >
                  <Send class="h-4 w-4" />
                </button>
              </div>
            </div>
          </SplitterPanel>

          <!-- Artifact 预览面板（开启时） -->
          <template v-if="artifact.open.value">
            <SplitterResizeHandle class="group relative w-px shrink-0 bg-zinc-200 transition-colors hover:bg-zinc-400 dark:bg-zinc-800 dark:hover:bg-zinc-600">
              <div class="absolute inset-y-0 -left-1.5 -right-1.5" />
            </SplitterResizeHandle>
            <SplitterPanel :default-size="42" :min-size="25" class="overflow-hidden border-l border-zinc-200/80 dark:border-zinc-800">
              <ArtifactPanel
                :artifacts="artifact.artifacts.value"
                :active-id="artifact.activeId.value"
                :active="artifact.active.value"
                :fullscreen="artifact.fullscreen.value"
                @select="artifact.select"
                @close="artifact.close"
                @toggle-fullscreen="artifact.fullscreen.value = !artifact.fullscreen.value"
              />
            </SplitterPanel>
          </template>
        </SplitterGroup>
      </template>
    </div>
  </div>
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
 */
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { SplitterGroup, SplitterPanel, SplitterResizeHandle } from 'radix-vue'
import { Plus, Trash2, Pencil, Send, CircleStop, MessageSquare, PanelLeft, ArrowDown, LayoutPanelLeft } from 'lucide-vue-next'
import type { ChatSession, ChatMessage, ChatModel, TokenUsage } from '@/types'
import { listModels, listSessions, createSession, renameSession, deleteSession, listMessages, sendMessageStream } from '@/api/chat'
import { AppButton, AppEmpty, AppLoading, AppSelect, AppTextarea, AppTooltip, toast, confirm } from '@/components/ui'
import ChatMessageList from '@/components/chat/ChatMessageList.vue'
import ArtifactPanel from '@/components/chat/ArtifactPanel.vue'
import { useStreamingMarkdown } from '@/composables/useStreamingMarkdown'
import { useThinkingPhases } from '@/composables/useThinkingPhases'
import { useArtifactPanel } from '@/composables/useArtifactPanel'

const sessionsLoading = ref(false)
const sessions = ref<ChatSession[]>([])
const currentSession = ref<ChatSession | null>(null)
const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const streaming = ref(false)
const caretFading = ref(false)
const models = ref<ChatModel[]>([])
const selectedModel = ref('')
const collapsed = ref(false)
const noAnimateIdx = ref(-1)

// 会话重命名内联编辑态
const renamingId = ref<number | null>(null)
const renameText = ref('')
const renameInputRef = ref<HTMLInputElement | HTMLInputElement[] | null>(null)

const listRef = ref<InstanceType<typeof ChatMessageList> | null>(null)
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
  return listRef.value?.scrollToBottom(force)
}
function scheduleScroll() {
  listRef.value?.scheduleScroll()
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

const modelOptions = computed(() => models.value.map((m) => ({ label: m.modelName, value: m.modelName })))

const displayUsage = computed<TokenUsage | null>(() => {
  if (usage.value) return usage.value
  if (streaming.value) {
    return { promptTokens: 0, completionTokens: Math.ceil(streamText.value.length / 4), totalTokens: 0 }
  }
  return null
})
const usageEstimating = computed(() => !usage.value && streaming.value)

async function loadModels() {
  try {
    models.value = await listModels()
    if (models.value.length && !selectedModel.value) {
      selectedModel.value = models.value[0].modelName
    }
  } catch {
    models.value = []
  }
}

onMounted(() => {
  loadModels()
  loadSessions()
})

async function loadSessions() {
  sessionsLoading.value = true
  try {
    sessions.value = await listSessions()
  } catch {
    sessions.value = []
  } finally {
    sessionsLoading.value = false
  }
}

async function selectSession(session: ChatSession) {
  cancelFade()
  artifact.close()
  currentSession.value = session
  messages.value = []
  try {
    messages.value = await listMessages(session.id)
  } catch {
    messages.value = []
  }
  scrollToBottom()
}

async function handleCreateSession() {
  try {
    const session = await createSession({ title: '新对话', modelName: selectedModel.value })
    sessions.value.unshift(session)
    selectSession(session)
  } catch {
    toast.error('创建对话失败')
  }
}

function startRename(session: ChatSession) {
  renamingId.value = session.id
  renameText.value = session.title
  nextTick(() => {
    const el = Array.isArray(renameInputRef.value) ? renameInputRef.value[0] : renameInputRef.value
    el?.focus()
    el?.select()
  })
}

function cancelRename() {
  renamingId.value = null
  renameText.value = ''
}

async function commitRename(session: ChatSession) {
  if (renamingId.value !== session.id) return
  const title = renameText.value.trim()
  renamingId.value = null
  if (!title || title === session.title) return
  const prev = session.title
  session.title = title // 乐观更新
  if (currentSession.value?.id === session.id) currentSession.value.title = title
  try {
    await renameSession(session.id, title)
  } catch {
    session.title = prev
    if (currentSession.value?.id === session.id) currentSession.value.title = prev
    toast.error('重命名失败')
  }
}

async function handleDeleteSession(id: number) {
  const ok = await confirm({ title: '删除确认', message: '确定删除该对话吗？', confirmText: '确定删除', danger: true })
  if (!ok) return
  try {
    await deleteSession(id)
    sessions.value = sessions.value.filter((s) => s.id !== id)
    if (currentSession.value?.id === id) {
      currentSession.value = null
      messages.value = []
    }
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
    messages.value = []
  }
}

onBeforeUnmount(() => {
  if (fadeTimer) clearTimeout(fadeTimer)
})

function onInputKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    handleSend()
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
      if (currentSession.value) currentSession.value.title = title
      const s = sessions.value.find((x) => x.id === currentSession.value?.id)
      if (s) s.title = title
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
