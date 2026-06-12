<template>
  <div class="flex h-[calc(100vh-104px)] overflow-hidden rounded-2xl border border-zinc-200/80 bg-white shadow-[0_10px_30px_-10px_rgba(0,0,0,0.04)] dark:border-zinc-800 dark:bg-zinc-900">
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
              ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
              : 'text-zinc-500 hover:bg-zinc-100/50 hover:text-zinc-800 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100',
            collapsed ? 'justify-center px-0' : ''
          ]"
          @click="selectSession(session)"
        >
          <AppTooltip v-if="collapsed" :content="session.title" side="right">
            <MessageSquare class="h-4 w-4 shrink-0" />
          </AppTooltip>
          <MessageSquare v-else class="h-4 w-4 shrink-0" />
          
          <span v-if="!collapsed" class="flex-1 truncate">{{ session.title }}</span>
          <button
            v-if="!collapsed"
            class="shrink-0 rounded-lg p-0.5 opacity-0 transition-all duration-200 ease-out group-hover:opacity-100"
            :class="currentSession?.id === session.id ? 'hover:bg-zinc-700 dark:hover:bg-zinc-300' : 'hover:bg-zinc-200 dark:hover:bg-zinc-700'"
            @click.stop="handleDeleteSession(session.id)"
          >
            <Trash2 class="h-3.5 w-3.5" />
          </button>
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
        <div class="flex h-14 items-center justify-between border-b border-zinc-200/80 px-5 dark:border-zinc-800">
          <span class="truncate text-sm font-semibold text-zinc-800 dark:text-zinc-100">{{ currentSession.title }}</span>
          <AppButton variant="ghost" size="sm" :icon="Trash2" @click="clearMessages">清空</AppButton>
        </div>

        <!-- 消息列表（外层为滚动容器，保持为原生 div 以便 useChatScroll 直接操作 scrollTop） -->
        <div ref="containerRef" class="flex flex-1 flex-col gap-5 overflow-y-auto p-5">
          <!--
            TransitionGroup 承载列表语义；逐条消息的入场动画由 VueUse Motion 的 v-motion 指令
            在各自挂载时独立驱动，互不影响 → 新消息插入既不会重播已有消息，也不会引起整列重排
            抖动。tag 设为 contents：不产生额外盒子，消息直接参与外层 flex 间距（gap-5）。
          -->
          <TransitionGroup tag="div" name="msg" class="contents">
            <div
              v-for="(msg, idx) in messages"
              :key="msg.id ?? `local-${idx}`"
              v-motion="messageMotion(msg.role, idx === noAnimateIdx)"
              class="flex items-start gap-3"
              :class="msg.role === 'user' ? 'flex-row-reverse' : ''"
            >
              <AppAvatar :icon="msg.role === 'user' ? User : Bot" :variant="msg.role === 'user' ? 'light' : 'dark'" />
              <div
                class="max-w-[72%] break-words rounded-2xl px-4 py-3 text-sm leading-relaxed"
                :class="msg.role === 'user' ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900' : 'bg-zinc-100/50 text-zinc-800 dark:bg-zinc-800 dark:text-zinc-100'"
              >
                <MarkdownView v-if="msg.role === 'assistant'" :content="msg.content" />
                <span v-else>{{ msg.content }}</span>
              </div>
            </div>
          </TransitionGroup>

          <!-- 流式输出中（流结束后仍保留 300ms 让光标平滑淡出，再提交进 messages） -->
          <div v-if="streaming || caretFading" v-motion="assistantMessageMotion" class="flex items-start gap-3">
            <AppAvatar :icon="Bot" variant="dark" />
            <div class="max-w-[72%] break-words rounded-2xl bg-zinc-100/50 px-4 py-3 text-sm leading-relaxed text-zinc-800 dark:bg-zinc-800 dark:text-zinc-100">
              <MarkdownView :content="streamDisplay" :caret="caretFading ? 'fade' : 'blink'" />
            </div>
          </div>

          <div v-if="messages.length === 0 && !streaming && !caretFading" class="flex flex-1 flex-col items-center justify-center gap-2 text-zinc-400 dark:text-zinc-500">
            <MessageSquare class="h-10 w-10 text-zinc-200 dark:text-zinc-700" />
            <p class="text-sm">发送消息开始对话</p>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="border-t border-zinc-200/80 bg-white px-4 py-3 dark:border-zinc-800 dark:bg-zinc-900">
          <div class="mb-2 flex items-center justify-between">
            <AppSelect
              v-model="selectedModel"
              :options="modelOptions"
              placeholder="选择模型"
              class="!w-44 max-w-44"
            />
            <span class="text-xs text-zinc-400 dark:text-zinc-500">Enter 发送 · Shift + Enter 换行</span>
          </div>
          <div class="flex items-end gap-2.5">
            <AppTextarea
              v-model="inputText"
              :rows="1"
              auto-grow
              placeholder="输入消息..."
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
              :class="inputText.trim() ? '' : 'pointer-events-none opacity-50'"
              @click="handleSend"
            >
              <Send class="h-4 w-4" />
            </button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * AI 对话页
 *
 * 1. 左侧会话列表：创建/切换/删除对话
 * 2. 右侧消息区：历史消息加载、流式输出（SSE）、Markdown 渲染
 * 3. 输入区：模型选择、Enter 发送 / Shift+Enter 换行、流式输出中途停止
 *
 * 流式消息通过 streamController（AbortController）支持用户主动中止；
 * 流结束后将 streamingContent 写入 messages，保持消息列表与流式态分离。
 * 流式期间通过 requestAnimationFrame 节流自动触底。
 */
import { Plus, Trash2, Bot, User, Send, CircleStop, MessageSquare, PanelLeft } from 'lucide-vue-next'
import type { ChatSession, ChatMessage, ChatModel } from '@/types'
import { listModels, listSessions, createSession, deleteSession, listMessages, sendMessageStream } from '@/api/chat'
import { toast, confirm, AppTooltip } from '@/components/ui'
import { useChatScroll } from '@/composables/useChatScroll'
import { useStreamingMarkdown } from '@/composables/useStreamingMarkdown'
import { messageMotion, assistantMessageMotion } from '@/composables/useMessageMotion'

const sessionsLoading = ref(false)
const sessions = ref<ChatSession[]>([])
const currentSession = ref<ChatSession | null>(null)
const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const streaming = ref(false)
// 光标淡出态：流结束后短暂为 true，使 ChatGPT 风格光标平滑淡出，期间气泡保持挂载
const caretFading = ref(false)
const models = ref<ChatModel[]>([])
const selectedModel = ref('')
const collapsed = ref(false)
// 流式气泡定稿后提交进 messages 的那条消息索引：该消息此前已可见，故跳过入场动画避免淡入重影
const noAnimateIdx = ref(-1)

const { containerRef, scrollToBottom, scheduleScroll } = useChatScroll()
// 流式态与历史态分离：流式期间 token 累加到 streamMd，结束后将 text 提交进 messages
const { text: streamText, display: streamDisplay, append: appendStream, flush: flushStream, reset: resetStream } = useStreamingMarkdown()
let streamController: AbortController | null = null
// 光标淡出定时器：切换会话/重新发送时需取消，避免把上一条流式结果误提交到新上下文
let fadeTimer: ReturnType<typeof setTimeout> | null = null
const CARET_FADE_MS = 300

/**
 * 流结束后的收尾：先进入淡出态让光标平滑消失，CARET_FADE_MS 后再把内容提交进 messages。
 * 提交与卸载淡出气泡同帧发生，内容一致，视觉上无缝衔接，且不会出现「气泡 + 定稿消息」双重渲染。
 */
function finalizeStream(finalText: string) {
  if (!finalText) {
    caretFading.value = false
    resetStream()
    return
  }
  caretFading.value = true
  fadeTimer = setTimeout(() => {
    fadeTimer = null
    // 该条已由流式气泡呈现，标记为不播放入场，避免提交瞬间二次淡入
    noAnimateIdx.value = messages.value.length
    messages.value.push({ role: 'assistant', content: finalText })
    caretFading.value = false
    resetStream()
    scrollToBottom()
  }, CARET_FADE_MS)
}

/** 取消进行中的淡出（会话切换/清空/卸载时调用），不提交滞留内容 */
function cancelFade() {
  if (fadeTimer) {
    clearTimeout(fadeTimer)
    fadeTimer = null
  }
  caretFading.value = false
  resetStream()
}

const modelOptions = computed(() => models.value.map((m) => ({ label: m.modelName, value: m.modelName })))

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

async function handleDeleteSession(id: number) {
  const ok = await confirm({
    title: '删除确认',
    message: '确定删除该对话吗？',
    confirmText: '确定删除',
    danger: true
  })
  if (!ok) return
  try {
    await deleteSession(id)
    sessions.value = sessions.value.filter(s => s.id !== id)
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
  const ok = await confirm({
    title: '清空确认',
    message: '确定清空当前对话记录吗？',
    confirmText: '确定清空',
    danger: true
  })
  if (ok) {
    cancelFade()
    messages.value = []
  }
}

onBeforeUnmount(() => {
  if (fadeTimer) clearTimeout(fadeTimer)
})

function onInputKeydown(e: KeyboardEvent) {
  // Enter 发送，Shift+Enter 换行；中文输入法组合期间不触发
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    handleSend()
  }
}

async function handleSend() {
  const content = inputText.value.trim()
  if (!content || streaming.value || !currentSession.value) return

  noAnimateIdx.value = -1 // 新一轮默认全部播放入场；仅下方「立即收尾」分支会标记跳过
  // 若上一条仍在淡出，立即收尾（提交其内容）再开始新一轮，避免丢失。
  // 该条已作为流式气泡可见，标记为不播放入场（用户消息索引随后递增，仍正常播放）。
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

  streaming.value = true
  resetStream()
  streamController = new AbortController()

  sendMessageStream(
    currentSession.value.id,
    content,
    (text) => {
      appendStream(text)
      scheduleScroll()
    },
    () => {
      // 流结束：先定稿快照，再交给 finalizeStream 走光标淡出 + 提交
      flushStream()
      const finalText = streamText.value
      streaming.value = false
      streamController = null
      finalizeStream(finalText)
    },
    (err) => {
      streaming.value = false
      streamController = null
      cancelFade()
      toast.error('发送失败：' + err)
    },
    streamController.signal
  )
}

function handleStop() {
  streamController?.abort()
}
</script>
