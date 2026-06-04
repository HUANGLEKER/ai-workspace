import request from './request'
import type { FileInfo, PageResult } from '@/types'

export const listFiles = (params: { page: number; size: number; fileName?: string }) =>
  request.get<unknown, PageResult<FileInfo>>('/file/list', { params })

export const deleteFile = (id: number) =>
  request.delete<unknown, void>(`/file/delete/${id}`)

export const getFileUrl = (filePath: string) =>
  request.get<unknown, string>('/file/url', { params: { filePath } })
