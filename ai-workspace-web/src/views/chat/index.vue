<template>
  <div class="flex h-[calc(100vh-104px)] overflow-hidden rounded-2xl border border-zinc-200/80 bg-white shadow-[0_10px_30px_-10px_rgba(0,0,0,0.04)]">
    <!-- 会话列表侧边栏 -->
    <div class="flex w-[240px] shrink-0 flex-col border-r border-zinc-200/80 bg-zinc-50">
      <div class="border-b border-zinc-200/80 p-3">
        <AppButton variant="primary" :icon="Plus" block @click="handleCreateSession">新建对话</AppButton>
      </div>

      <div class="relative flex-1 overflow-y-auto p-2">
        <div
          v-for="session in sessions"
          :key="session.id"
          class="group mb-0.5 flex cursor-pointer items-center gap-2 rounded-xl px-3 py-2.5 text-sm transition-all duration-200 ease-out"
          :class="currentSession?.id === session.id
            ? 'bg-zinc-900 text-white'
            : 'text-zinc-500 hover:bg-zinc-100/50 hover:text-zinc-800'"
          @click="selectSession(session)"
        >
          <MessageSquare class="h-3.5 w-3.5 shrink-0" />
          <span class="flex-1 truncate">{{ session.title }}</span>
          <button
            class="shrink-0 rounded-lg p-0.5 opacity-0 transition-all duration-200 ease-out group-hover:opacity-100"
            :class="currentSession?.id === session.id ? 'hover:bg-zinc-700' : 'hover:bg-zinc-200'"
            @click.stop="handleDeleteSession(session.id)"
          >
            <Trash2 class="h-3.5 w-3.5" />
          </button>
        </div>

        <AppEmpty v-if="!sessionsLoading && sessions.length === 0" description="暂无对话" />
        <AppLoading v-if="sessionsLoading" overlay />
      </div>
    </div>

    <!-- 对话主区域 -->
    <div class="flex flex-1 flex-col overflow-hidden">
      <!-- 未选择对话时的空态 -->
      <div v-if="!currentSession" class="flex flex-1 flex-col items-center justify-center gap-3 text-zinc-400">
        <MessageSquare class="h-14 w-14 text-zinc-200" />
        <p class="text-sm">选择左侧对话，或点击「新建对话」开始</p>
      </div>

      <template v-else>
        <!-- 对话标题栏 -->
        <div class="flex h-14 items-center justify-between border-b border-zinc-200/80 px-5">
          <span class="truncate text-sm font-semibold text-zinc-800">{{ currentSession.title }}</span>
          <AppButton variant="ghost" size="sm" :icon="Trash2" @click="clearMessages">清空</AppButton>
        </div>

        <!-- 消息列表 -->
        <div ref="messagesRef" class="flex flex-1 flex-col gap-5 overflow-y-auto p-5">
          <div
            v-for="(msg, idx) in messages"
            :key="idx"
            class="flex items-start gap-3"
            :class="msg.role === 'user' ? 'flex-row-reverse' : ''"
          >
            <AppAvatar :icon="msg.role === 'user' ? User : Bot" :variant="msg.role === 'user' ? 'light' : 'dark'" />
            <div
              class="max-w-[72%] break-words rounded-2xl px-4 py-3 text-sm leading-relaxed"
              :class="msg.role === 'user' ? 'bg-zinc-900 text-white' : 'bg-zinc-100/50 text-zinc-800'"
            >
              <div v-if="msg.role === 'assistant'" class="prose prose-zinc max-w-none prose-pre:overflow-x-auto" v-html="renderMd(msg.content)" />
              <span v-else>{{ msg.content }}</span>
            </div>
          </div>

          <!-- 流式输出中 -->
          <div v-if="streaming" class="flex items-start gap-3">
            <AppAvatar :icon="Bot" variant="dark" />
            <div class="max-w-[72%] break-words rounded-2xl bg-zinc-100/50 px-4 py-3 text-sm leading-relaxed text-zinc-800">
              <div class="prose prose-zinc max-w-none prose-pre:overflow-x-auto" v-html="renderMd(streamingContent)" />
              <span class="inline-block animate-pulse font-bold text-zinc-800">▋</span>
            </div>
          </div>

          <div v-if="messages.length === 0 && !streaming" class="flex flex-1 flex-col items-center justify-center gap-2 text-zinc-400">
            <MessageSquare class="h-10 w-10 text-zinc-200" />
            <p class="text-sm">发送消息开始对话</p>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="border-t border-zinc-200/80 bg-white px-4 py-3">
          <div class="mb-2 flex items-center justify-between">
            <AppSelect
              v-model="selectedModel"
              :options="modelOptions"
              placeholder="选择模型"
              class="!w-44 max-w-44"
            />
            <span class="text-xs text-zinc-400">Enter 发送 · Shift + Enter 换行</span>
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
              class="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-zinc-900 text-white transition-all duration-200 ease-out hover:bg-zinc-800"
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
import { ref, computed, nextTick, onMounted } from 'vue'
import { Plus, Trash2, Bot, User, Send, CircleStop, MessageSquare } from 'lucide-vue-next'
import MarkdownIt from 'markdown-it'
import type { ChatSession, ChatMessage, ChatModel } from '@/types'
import { listModels, listSessions, createSession, deleteSession, listMessages, sendMessageStream } from '@/api/chat'
import { AppButton, AppSelect, AppTextarea, AppAvatar, AppEmpty, AppLoading, toast, confirm } from '@/components/ui'

const md = new MarkdownIt({ html: false, linkify: true, typographer: true })
const renderMd = (content: string) => md.render(content || '')

const sessionsLoading = ref(false)
const sessions = ref<ChatSession[]>([])
const currentSession = ref<ChatSession | null>(null)
const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const streaming = ref(false)
const streamingContent = ref('')
const models = ref<ChatModel[]>([])
const selectedModel = ref('')
const messagesRef = ref<HTMLElement>()
let streamController: AbortController | null = null
// rAF 句柄：流式 token 频繁到达时合并滚动请求，避免布局抖动
let scrollRaf = 0

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

const scrollToBottom = async () => {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

function scheduleScroll() {
  if (scrollRaf) return
  scrollRaf = requestAnimationFrame(() => {
    scrollRaf = 0
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
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
  if (ok) messages.value = []
}

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

  inputText.value = ''
  messages.value.push({ role: 'user', content })
  await scrollToBottom()

  streaming.value = true
  streamingContent.value = ''
  streamController = new AbortController()

  sendMessageStream(
    currentSession.value.id,
    content,
    (text) => {
      streamingContent.value += text
      scheduleScroll()
    },
    () => {
      // 流结束后将累积内容写入消息列表，流式态与历史态分离，避免双重渲染
      if (streamingContent.value) {
        messages.value.push({ role: 'assistant', content: streamingContent.value })
      }
      streaming.value = false
      streamingContent.value = ''
      streamController = null
      scrollToBottom()
    },
    (err) => {
      streaming.value = false
      streamingContent.value = ''
      streamController = null
      toast.error('发送失败：' + err)
    },
    streamController.signal
  )
}

function handleStop() {
  streamController?.abort()
}
</script>
