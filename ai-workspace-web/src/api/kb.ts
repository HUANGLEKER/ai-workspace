/**
 * 知识库与 RAG 相关 API
 *
 * 包含：
 * 1. 知识库 CRUD（按 createBy 隔离，后端仅返回本人数据）
 * 2. 文档管理（上传由 el-upload 直传，此处仅负责列表与删除）
 * 3. ragChatStream：SSE 流式 RAG 问答，复用 streamSSE 工具
 */
import request from './request'
import { streamSSE } from './sse'
import type { KnowledgeBase, KbDocument, PageResult, RagSource } from '@/types'

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

/**
 * 对指定知识库进行 RAG 流式问答。
 *
 * 复用共享 streamSSE 传输层，后端代理两种帧格式：
 * - 一次性的 {type:'sources', sources:[...]} 元数据帧，通过 onSources 回调传出
 * - 反复出现的 {content:'<token>'} token 帧，通过 onChunk 回调传出
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
        // sources 帧只触发回调，不向聊天气泡输出文本
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
