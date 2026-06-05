import request from './request'
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

export const sendMessageStream = async (
  sessionId: number,
  content: string,
  onChunk: (text: string) => void,
  onDone: () => void,
  onError: (err: string) => void
) => {
  const token = localStorage.getItem('token')
  try {
    const response = await fetch('/api/chat/send', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: JSON.stringify({ sessionId, content })
    })

    if (!response.ok) {
      onError(`请求失败：${response.status}`)
      return
    }

    const reader = response.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    const handleLine = (line: string): boolean => {
      if (!line.startsWith('data: ')) return false
      const data = line.slice(6).trim()
      if (data === '[DONE]') {
        onDone()
        return true
      }
      try {
        const parsed = JSON.parse(data)
        const text = parsed.content ?? parsed.text ?? parsed.delta ?? ''
        if (text) onChunk(text)
      } catch {
        if (data) onChunk(data)
      }
      return false
    }

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      // Keep the last (possibly incomplete) line in the buffer until the next read.
      const lines = buffer.split('\n')
      buffer = lines.pop() ?? ''
      for (const line of lines) {
        if (handleLine(line)) return
      }
    }
    // Flush any remaining buffered line after the stream closes.
    if (buffer && handleLine(buffer)) return
    onDone()
  } catch (e) {
    onError((e as Error).message || '连接失败')
  }
}
