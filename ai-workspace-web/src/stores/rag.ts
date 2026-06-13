/**
 * 知识库问答数据状态 Store
 *
 * 与 chat store 同构：持有知识库列表 / 模型列表 / 问答会话列表 / 当前会话 /
 * 消息数组等「数据状态」与其 CRUD 动作。问答会话独立于 chat 会话（rag_session 表），
 * 额外绑定一个知识库——会话列表按当前选中知识库过滤。
 *
 * 边界约定同 chat store：流式管线 / Thinking / Artifact 等 UI 交互状态留在视图组件，
 * Store 只负责数据与乐观更新/回滚。
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { RagSession, ChatMessage, ChatModel, KnowledgeBase } from '@/types'
import { listModels } from '@/api/chat'
import {
  listKnowledgeBases,
  listRagSessions,
  createRagSession as apiCreateSession,
  renameRagSession as apiRenameSession,
  deleteRagSession as apiDeleteSession,
  listRagMessages
} from '@/api/kb'

export const useRagStore = defineStore('rag', () => {
  const knowledgeBases = ref<KnowledgeBase[]>([])
  const selectedKbId = ref<number>()

  const models = ref<ChatModel[]>([])
  const selectedModel = ref('')

  const sessions = ref<RagSession[]>([])
  const sessionsLoading = ref(false)
  const currentSession = ref<RagSession | null>(null)
  const messages = ref<ChatMessage[]>([])

  const kbOptions = computed(() => knowledgeBases.value.map((kb) => ({ label: kb.kbName, value: kb.id })))
  const selectedKbName = computed(() => knowledgeBases.value.find((kb) => kb.id === selectedKbId.value)?.kbName ?? '')
  const modelOptions = computed(() => models.value.map((m) => ({ label: m.modelName, value: m.modelName })))

  async function loadKbs() {
    try {
      knowledgeBases.value = await listKnowledgeBases()
    } catch {
      knowledgeBases.value = []
    }
  }

  async function loadModels() {
    try {
      models.value = await listModels()
      if (models.value.length && !selectedModel.value) selectedModel.value = models.value[0].modelName
    } catch {
      models.value = []
    }
  }

  /** 加载当前知识库下的问答会话列表 */
  async function loadSessions() {
    if (!selectedKbId.value) {
      sessions.value = []
      return
    }
    sessionsLoading.value = true
    try {
      sessions.value = await listRagSessions(selectedKbId.value)
    } catch {
      sessions.value = []
    } finally {
      sessionsLoading.value = false
    }
  }

  /** 切换知识库：重载会话并清空当前会话/消息 */
  async function selectKb(kbId: number | undefined) {
    selectedKbId.value = kbId
    currentSession.value = null
    messages.value = []
    await loadSessions()
  }

  /** 切换当前会话并加载其消息历史 */
  async function selectSession(session: RagSession) {
    currentSession.value = session
    messages.value = []
    try {
      messages.value = await listRagMessages(session.id)
    } catch {
      messages.value = []
    }
  }

  /** 新建会话（绑定当前知识库与选中模型）并置为当前会话 */
  async function createSession(): Promise<RagSession> {
    if (!selectedKbId.value) throw new Error('未选择知识库')
    const session = await apiCreateSession({
      kbId: selectedKbId.value,
      title: '新问答',
      modelName: selectedModel.value
    })
    sessions.value.unshift(session)
    return session
  }

  /** 重命名（乐观更新，失败回滚并抛出） */
  async function renameSessionTitle(session: RagSession, title: string) {
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

  function clearMessages() {
    messages.value = []
  }

  return {
    knowledgeBases,
    selectedKbId,
    models,
    selectedModel,
    sessions,
    sessionsLoading,
    currentSession,
    messages,
    kbOptions,
    selectedKbName,
    modelOptions,
    loadKbs,
    loadModels,
    loadSessions,
    selectKb,
    selectSession,
    createSession,
    renameSessionTitle,
    removeSession,
    setSessionTitle,
    clearMessages
  }
})
