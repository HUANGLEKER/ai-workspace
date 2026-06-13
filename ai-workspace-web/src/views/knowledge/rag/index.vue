<template>
  <div class="flex flex-1 overflow-hidden bg-white dark:bg-zinc-900">
    <!-- 会话列表侧边栏 -->
    <div
      class="relative flex shrink-0 flex-col border-r border-zinc-200/80 bg-zinc-50 transition-all duration-200 ease-out dark:border-zinc-800 dark:bg-zinc-900"
      :class="collapsed ? 'w-[72px]' : 'w-[260px]'"
    >
      <!-- 知识库选择 + 新建会话 -->
      <div class="flex flex-col gap-2 border-b border-zinc-200/80 p-3 dark:border-zinc-800">
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
        <AppButton v-else variant="primary" :icon="Plus" block class="px-0" :disabled="!selectedKbId" @click="handleCreateSession"></AppButton>
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
            <FileSearch class="h-4 w-4 shrink-0" />
          </AppTooltip>
          <FileSearch v-else class="h-4 w-4 shrink-0" />

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

        <AppEmpty v-if="!sessionsLoading && selectedKbId && sessions.length === 0 && !collapsed" description="暂无问答会话" />
        <AppLoading v-if="sessionsLoading" overlay />
      </div>

      <button
        class="flex h-11 shrink-0 items-center justify-center border-t border-zinc-200/80 text-zinc-500 transition-all duration-200 ease-out hover:bg-zinc-100/50 hover:text-zinc-800 dark:border-zinc-800 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100"
        @click="collapsed = !collapsed"
      >
        <PanelLeft class="h-4 w-4" />
      </button>
    </div>

    <!-- 问答主区域 -->
    <div class="flex flex-1 flex-col overflow-hidden">
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
        <div class="flex h-14 shrink-0 items-center justify-between border-b border-zinc-200/80 px-5 dark:border-zinc-800">
          <div class="flex min-w-0 items-center gap-2">
            <span class="truncate text-sm font-semibold text-zinc-800 dark:text-zinc-100">{{ currentSession.title }}</span>
            <span class="flex shrink-0 items-center gap-1 rounded-md bg-zinc-100 px-1.5 py-0.5 text-xs text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400">
              <BookOpen class="h-3 w-3" />{{ selectedKbName }}
            </span>
          </div>
          <div class="flex items-center gap-1">
            <AppButton v-if="artifact.artifacts.value.length && !artifact.open.value" variant="ghost" size="sm" :icon="LayoutPanelLeft" @click="artifact.show(artifact.artifacts.value)">Artifact</AppButton>
            <AppButton variant="ghost" size="sm" :icon="Trash2" @click="clearMessages">清空</AppButton>
          </div>
        </div>

        <SplitterGroup direction="horizontal" class="flex flex-1 overflow-hidden">
          <SplitterPanel :default-size="artifact.open.value ? 58 : 100" :min-size="34" class="flex flex-col overflow-hidden">
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
                :live-sources="liveSources"
                @copy="copyMessage"
                @regenerate="regenerateMessage"
                @delete="deleteMessage"
                @open-artifact="(a) => artifact.show(a)"
              />

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
            <div class="relative shrink-0 border-t border-zinc-200/80 bg-white px-4 py-3 dark:border-zinc-800 dark:bg-zinc-900">
              <div class="mb-2 flex items-center justify-between">
                <div class="flex items-center gap-2">
                  <AppSelect v-model="selectedModel" :options="modelOptions" placeholder="默认模型" class="!w-44 max-w-44" />
                  <button
                    type="button"
                    :disabled="streaming"
                    class="flex items-center gap-1.5 rounded-lg border px-2.5 py-1.5 text-xs font-medium transition-all duration-200 ease-out disabled:opacity-50"
                    :class="webSearch
                      ? 'border-zinc-900 bg-zinc-900 text-white dark:border-zinc-100 dark:bg-zinc-100 dark:text-zinc-900'
                      : 'border-zinc-200/80 text-zinc-500 hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-400 dark:hover:bg-zinc-800'"
                    :title="webSearch ? '联网搜索已开启' : '联网搜索已关闭'"
                    @click="webSearch = !webSearch"
                  >
                    <Globe class="h-3.5 w-3.5" />
                    联网
                  </button>
                </div>
                <span class="text-xs text-zinc-400 dark:text-zinc-500">Enter 提问 · Shift + Enter 换行</span>
              </div>
              <div class="flex items-end gap-2.5">
                <AppTextarea v-model="inputText" :rows="1" auto-grow placeholder="输入你的问题，回答将引用文档内容..." @keydown="onInputKeydown" />
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
                :versions="artifact.versions.value"
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
 * 知识库问答页（RAG）
 *
 * 与 AI 对话页对齐的完整能力：会话历史持久化（左侧会话列表 + 多轮上下文）、
 * 流式输出、Markdown/代码实时渲染、Thinking 阶段、Artifact 分屏、Token 用量、
 * 消息复制/重新生成/删除。RAG 特有：每条回答附带可折叠的引用来源卡片（命中高亮）。
 *
 * 数据状态收敛在 rag store；本组件保留流式管线与交互状态（与 chat 页同构）。
 */
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { SplitterGroup, SplitterPanel, SplitterResizeHandle } from 'radix-vue'
import { Plus, Trash2, Pencil, Send, CircleStop, FileSearch, BookOpen, PanelLeft, ArrowDown, LayoutPanelLeft, Globe } from 'lucide-vue-next'
import { storeToRefs } from 'pinia'
import type { RagSession, ChatMessage, RagSource, TokenUsage } from '@/types'
import { ragChatStream, clearRagMessages } from '@/api/kb'
import { useRagStore } from '@/stores/rag'
import { AppButton, AppEmpty, AppLoading, AppSelect, AppTextarea, AppTooltip, toast, confirm } from '@/components/ui'
import ChatMessageList from '@/components/chat/ChatMessageList.vue'
import ArtifactPanel from '@/components/chat/ArtifactPanel.vue'
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
const collapsed = ref(false)
const noAnimateIdx = ref(-1)

// 当前流式轮次已下发的引用来源（先于 token 到达），挂在 live 气泡下方
const liveSources = ref<RagSource[]>([])

const renamingId = ref<number | null>(null)
const renameText = ref('')
const renameInputRef = ref<HTMLInputElement | HTMLInputElement[] | null>(null)

const listRef = ref<InstanceType<typeof ChatMessageList> | null>(null)
const thinkingState = useThinkingPhases()
const artifact = useArtifactPanel()

const usage = ref<TokenUsage | null>(null)
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

function startRename(session: RagSession) {
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

async function commitRename(session: RagSession) {
  if (renamingId.value !== session.id) return
  const title = renameText.value.trim()
  renamingId.value = null
  if (!title || title === session.title) return
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
