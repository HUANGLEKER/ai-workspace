import request from './request'
import { streamSSE } from './sse'
import type { ChatSession, ChatMessage, ChatModel, PageResult } from '@/types'

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
  request.delete<unknown, void>(`/chat/model/delete/${id}`)

export const toggleModelStatus = (id: number, enabled: number) =>
  request.put<unknown, void>('/chat/model/status', { id, enabled })

export const listSessions = () =>
  request.get<unknown, ChatSession[]>('/chat/session/list')

export const createSession = (data: { title?: string; modelName?: string }) =>
  request.post<unknown, ChatSession>('/chat/session/create', data)

export const deleteSession = (id: number) =>
  request.delete<unknown, void>(`/chat/session/delete/${id}`)

export const listMessages = (sessionId: number) =>
  request.get<unknown, ChatMessage[]>('/chat/message/list', { params: { sessionId } })

export const sendMessageStream = (
  sessionId: number,
  content: string,
  onChunk: (text: string) => void,
  onDone: () => void,
  onError: (err: string) => void,
  signal?: AbortSignal
) => streamSSE('/api/chat/send', { sessionId, content }, { onChunk, onDone, onError, signal })
