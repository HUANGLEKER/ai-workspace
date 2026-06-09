/**
 * 提示词中心 API
 *
 * 提示词按 createBy 用户私有，支持按标题关键词和分类筛选。
 */
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
