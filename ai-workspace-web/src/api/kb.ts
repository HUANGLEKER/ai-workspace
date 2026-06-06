import request from './request'
import { streamSSE } from './sse'
import type { KnowledgeBase, KbDocument, PageResult, RagSource } from '@/types'

export const listKnowledgeBases = () =>
  request.get<unknown, KnowledgeBase[]>('/kb/list')

export const createKnowledgeBase = (data: { kbName: string; description: string }) =>
  request.post<unknown, KnowledgeBase>('/kb/create', data)

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

/**
 * Stream a RAG answer for a question against a knowledge base.
 *
 * Reuses the shared {@link streamSSE} transport. The backend proxies two frame
 * shapes: a one-off `{type:'sources', sources:[...]}` metadata frame (surfaced
 * via {@link onSources}) and repeated `{content:'<token>'}` token frames
 * (surfaced via {@link onChunk}). Returns the promise from `streamSSE`.
 */
export const ragChatStream = (
  params: { kbId: number; question: string; sessionId?: string; topK?: number },
  onChunk: (text: string) => void,
  onSources: (sources: RagSource[]) => void,
  onDone: () => void,
  onError: (err: string) => void,
  signal?: AbortSignal
) =>
  streamSSE('/api/rag/chat', params, {
    onChunk,
    onDone,
    onError,
    signal,
    extract: (data) => {
      try {
        const frame = JSON.parse(data)
        // metadata frame — capture sources, emit nothing as text
        if (frame.type === 'sources') {
          onSources(frame.sources ?? [])
          return undefined
        }
        return frame.content ?? frame.token ?? undefined
      } catch {
        return undefined
      }
    }
  })
