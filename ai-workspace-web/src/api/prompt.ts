import request from './request'

export interface Prompt {
  id?: number
  title: string
  content: string
  category?: string
  description?: string
  createTime?: string
  updateTime?: string
}

export const listPrompts = (params?: { keyword?: string; category?: string }) =>
  request.get<unknown, Prompt[]>('/prompt/list', { params })
export const addPrompt = (data: Prompt) => request.post('/prompt/add', data)
export const updatePrompt = (data: Prompt) => request.put('/prompt/update', data)
export const deletePrompt = (id: number) => request.delete(`/prompt/delete/${id}`)
