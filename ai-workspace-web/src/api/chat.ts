/**
 * Chat 相关 API
 *
 * 包含两类：
 * 1. 模型管理（管理员专属）：增删查改、分页、启用/禁用
 * 2. 会话与消息：创建/列出/删除会话，加载历史消息，发送流式消息
 *
 * sendMessageStream 使用原生 fetch SSE（非 Axios），
 * 通过共享的 streamSSE 工具处理传输层细节。
 */
import request from './request'
import { streamSSE } from './sse'
import type { ChatSession, ChatMessage, ChatModel, PageResult, TokenUsage } from '@/types'

export const listModels = () =>
  request.get<unknown, ChatModel[]>('/chat/model/list')

// --- 模型管理（管理员） ---
export const pageModels = (params: { page: number; size: number; modelName?: string }) =>
  request.get<unknown, PageResult<ChatModel>>('/chat/model/page', { params })

export const addModel = (data: ChatModel) =>
  request.post<unknown, void>('/chat/model/add', data)

export const updateModel = (data: ChatModel) =>
  request.put<unknown, void>('/chat/model/update', data)

export const deleteModel = (id: number) =>
  request.delete<unknown, void>(`/chat/model/${id}`)

export const toggleModelStatus = (id: number, enabled: number) =>
  request.put<unknown, void>('/chat/model/status', { id, enabled })

export const listSessions = () =>
  request.get<unknown, ChatSession[]>('/chat/session/list')

export const createSession = (data: { title?: string; modelName?: string }) =>
  request.post<unknown, ChatSession>('/chat/session/add', data)

export const renameSession = (id: number, title: string) =>
  request.put<unknown, void>(`/chat/session/${id}`, { title })

export const updateSessionPrompt = (id: number, systemPrompt: string) =>
  request.put<unknown, void>(`/chat/session/${id}/prompt`, { systemPrompt })

export const deleteSession = (id: number) =>
  request.delete<unknown, void>(`/chat/session/${id}`)

export const listMessages = (sessionId: number) =>
  request.get<unknown, ChatMessage[]>(`/chat/session/${sessionId}/messages`)

export const sendMessageStream = (
  sessionId: number,
  content: string,
  onChunk: (text: string) => void,
  onDone: () => void,
  onError: (err: string) => void,
  signal?: AbortSignal,
  onUsage?: (usage: TokenUsage) => void,
  onTitle?: (title: string) => void
) =>
  streamSSE(
    '/api/chat/send',
    { sessionId, content },
    {
      onChunk,
      onDone,
      onError,
      signal,
      // token 统计帧 / 自动标题帧不进入正文渲染，经 onMeta 旁路解析后分发
      onMeta:
        onUsage || onTitle
          ? (data) => {
              try {
                const j = JSON.parse(data)
                if (j.type === 'usage' && onUsage) {
                  onUsage({
                    promptTokens: j.prompt_tokens ?? 0,
                    completionTokens: j.completion_tokens ?? 0,
                    totalTokens: j.total_tokens ?? 0
                  })
                } else if (j.type === 'title' && onTitle && j.title) {
                  onTitle(j.title)
                }
              } catch {
                /* 非 JSON 元数据帧，忽略 */
              }
            }
          : undefined
    }
  )
