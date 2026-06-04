import request from './request'
import type { KnowledgeBase, KbDocument, PageResult } from '@/types'

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
