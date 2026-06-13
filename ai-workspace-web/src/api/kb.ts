/**
 * 知识库与 RAG 相关 API
 *
 * 包含：
 * 1. 知识库 CRUD（按 createBy 隔离，后端仅返回本人数据）
 * 2. 文档管理（上传由 AppUpload 直传，此处仅负责列表与删除）
 * 3. ragChatStream：SSE 流式 RAG 问答，复用 streamSSE 工具
 */
import request from './request'
import { streamSSE } from './sse'
import type { KnowledgeBase, KbDocument, PageResult, RagSource, RagSession, ChatMessage, TokenUsage } from '@/types'

export const listKnowledgeBases = () =>
  request.get<unknown, KnowledgeBase[]>('/kb/list')

export const createKnowledgeBase = (data: { kbName: string; description: string }) =>
  request.post<unknown, KnowledgeBase>('/kb/add', data)

export const updateKnowledgeBase = (data: Partial<KnowledgeBase>) =>
  request.put<unknown, void>('/kb/update', data)

export const deleteKnowledgeBase = (id: number) =>
  request.delete<unknown, void>(`/kb/delete/${id}`)

export const listDocuments = (params: { kbId: number; page?: number; size?: number }) =>
  request.get<unknown, PageResult<KbDocument>>('/document/list', { params })

export const deleteDocument = (id: number) =>
  request.delete<unknown, void>(`/document/delete/${id}`)

export const rebuildRag = (kbId: number) =>
  request.post<unknown, void>('/rag/rebuild', { kbId })

// ─── 知识库问答会话（历史持久化 + 多轮上下文） ─────────────────────────

export const listRagSessions = (kbId?: number) =>
  request.get<unknown, RagSession[]>('/rag/session/list', { params: kbId ? { kbId } : {} })

export const createRagSession = (data: { kbId: number; title?: string; modelName?: string }) =>
  request.post<unknown, RagSession>('/rag/session/add', data)

export const renameRagSession = (id: number, title: string) =>
  request.put<unknown, void>(`/rag/session/${id}`, { title })

export const updateRagSessionPrompt = (id: number, systemPrompt: string) =>
  request.put<unknown, void>(`/rag/session/${id}/prompt`, { systemPrompt })

export const deleteRagSession = (id: number) =>
  request.delete<unknown, void>(`/rag/session/${id}`)

export const clearRagMessages = (id: number) =>
  request.delete<unknown, void>(`/rag/session/${id}/messages`)

/**
 * 加载会话消息历史。后端把 assistant 消息的 sources 以 JSON 字符串存库，
 * 此处反序列化为 RagSource[] 供组件直接渲染来源卡片。
 */
export const listRagMessages = async (sessionId: number): Promise<ChatMessage[]> => {
  const raw = await request.get<unknown, (ChatMessage & { sources?: unknown })[]>(
    `/rag/session/${sessionId}/messages`
  )
  return raw.map((m) => ({
    ...m,
    sources: typeof m.sources === 'string' && m.sources ? safeParseSources(m.sources) : undefined
  }))
}

function safeParseSources(s: string): RagSource[] | undefined {
  try {
    const arr = JSON.parse(s)
    return Array.isArray(arr) ? arr : undefined
  } catch {
    return undefined
  }
}

/**
 * 对指定会话进行 RAG 流式问答（多轮，历史由后端按 sessionId 组装）。
 *
 * 复用共享 streamSSE 传输层；token 帧经 extract 输出到 onChunk，
 * 其余结构化帧（sources/usage/title）经 onMeta 旁路分发，与正文渲染解耦。
 */
export const ragChatStream = (
  params: { sessionId: number; question: string; topK?: number; model?: string; webSearch?: boolean },
  onChunk: (text: string) => void,
  onSources: (sources: RagSource[]) => void,
  onDone: () => void,
  onError: (err: string) => void,
  signal?: AbortSignal,
  opts?: { onUsage?: (usage: TokenUsage) => void; onTitle?: (title: string) => void; onStatus?: (status: string) => void }
) =>
  streamSSE('/api/rag/chat', params, {
    onChunk,
    onDone,
    onError,
    signal,
    // 仅 token 帧映射为展示文本；其它帧返回 undefined 交给 onMeta 处理
    extract: (data) => {
      try {
        const frame = JSON.parse(data)
        if (frame.type === 'token') return frame.token ?? undefined
        return frame.content ?? undefined
      } catch {
        return undefined
      }
    },
    onMeta: (data) => {
      try {
        const j = JSON.parse(data)
        if (j.type === 'sources') onSources(j.sources ?? [])
        else if (j.type === 'usage' && opts?.onUsage) {
          opts.onUsage({
            promptTokens: j.prompt_tokens ?? 0,
            completionTokens: j.completion_tokens ?? 0,
            totalTokens: j.total_tokens ?? 0
          })
        } else if (j.type === 'title' && opts?.onTitle && j.title) opts.onTitle(j.title)
        else if (j.type === 'status' && opts?.onStatus && j.status) opts.onStatus(j.status)
      } catch {
        /* 非 JSON 元数据帧，忽略 */
      }
    }
  })
