/**
 * Chat 数据状态 Store（P1-4）
 *
 * 持有会话列表 / 当前会话 / 消息数组 / 模型列表等「数据状态」与其 CRUD 动作，
 * 供 chat 页及后续多窗口、会话引用等场景复用。
 *
 * 边界约定：流式管线（streaming/caret/AbortController）、Thinking 阶段、
 * Artifact 面板、重命名输入框等「UI/交互状态」不进 Store，留在视图组件——
 * 它们与组件生命周期强绑定，跨组件共享反而引入悬挂引用。
 * toast 等用户反馈也由调用方处理，Store 只负责数据与乐观更新/回滚。
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ChatSession, ChatMessage, ChatModel } from '@/types'
import {
  listModels,
  listSessions,
  createSession as apiCreateSession,
  renameSession as apiRenameSession,
  deleteSession as apiDeleteSession,
  listMessages
} from '@/api/chat'

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<ChatSession[]>([])
  const sessionsLoading = ref(false)
  const currentSession = ref<ChatSession | null>(null)
  const messages = ref<ChatMessage[]>([])
  const models = ref<ChatModel[]>([])
  const selectedModel = ref('')

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

  /** 切换当前会话并加载其消息历史 */
  async function selectSession(session: ChatSession) {
    currentSession.value = session
    messages.value = []
    try {
      messages.value = await listMessages(session.id)
    } catch {
      messages.value = []
    }
  }

  /** 新建会话（绑定当前选中模型）并置为当前会话；失败时抛出由调用方提示 */
  async function createSession(): Promise<ChatSession> {
    const session = await apiCreateSession({ title: '新对话', modelName: selectedModel.value })
    sessions.value.unshift(session)
    return session
  }

  /** 重命名（乐观更新，失败回滚并抛出） */
  async function renameSessionTitle(session: ChatSession, title: string) {
    const prev = session.title
    setSessionTitle(session.id, title)
    try {
      await apiRenameSession(session.id, title)
    } catch (e) {
      setSessionTitle(session.id, prev)
      throw e
    }
  }

  /** 删除会话；若删除的是当前会话则同时清空消息区 */
  async function removeSession(id: number) {
    await apiDeleteSession(id)
    sessions.value = sessions.value.filter((s) => s.id !== id)
    if (currentSession.value?.id === id) {
      currentSession.value = null
      messages.value = []
    }
  }

  /** 同步更新列表项与当前会话的标题（重命名/后端自动起名共用） */
  function setSessionTitle(id: number, title: string) {
    const s = sessions.value.find((x) => x.id === id)
    if (s) s.title = title
    if (currentSession.value?.id === id) currentSession.value.title = title
  }

  function pushMessage(msg: ChatMessage) {
    messages.value.push(msg)
  }

  function clearMessages() {
    messages.value = []
  }

  return {
    sessions,
    sessionsLoading,
    currentSession,
    messages,
    models,
    selectedModel,
    modelOptions,
    loadModels,
    loadSessions,
    selectSession,
    createSession,
    renameSessionTitle,
    removeSession,
    setSessionTitle,
    pushMessage,
    clearMessages
  }
})
