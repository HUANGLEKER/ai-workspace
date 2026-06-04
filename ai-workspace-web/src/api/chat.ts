import request from './request'
import type { ChatSession, ChatMessage } from '@/types'

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

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      const chunk = decoder.decode(value, { stream: true })
      const lines = chunk.split('\n')
      for (const line of lines) {
        if (line.startsWith('data: ')) {
          const data = line.slice(6).trim()
          if (data === '[DONE]') {
            onDone()
            return
          }
          try {
            const parsed = JSON.parse(data)
            const text = parsed.content ?? parsed.text ?? parsed.delta ?? ''
            if (text) onChunk(text)
          } catch {
            if (data) onChunk(data)
          }
        }
      }
    }
    onDone()
  } catch (e) {
    onError((e as Error).message || '连接失败')
  }
}
