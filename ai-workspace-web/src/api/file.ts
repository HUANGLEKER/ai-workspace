/**
 * 文件中心 API
 *
 * 文件实际存储在 MinIO，后端通过 /file/presign/:id 返回预签名 URL 供浏览器直接下载。
 * 上传由 AppUpload 组件直传 /api/file/upload，此处不封装上传逻辑。
 * 列表按 uploadBy 隔离（非 createBy），见 CLAUDE.md 归属说明。
 */
import request from './request'
import type { FileInfo, PageResult } from '@/types'

export const listFiles = (params: { page: number; size: number; fileName?: string }) =>
  request.get<unknown, PageResult<FileInfo>>('/file/list', { params })

export const deleteFile = (id: number) =>
  request.delete<unknown, void>(`/file/delete/${id}`)

export const getFileUrl = async (id: number) => {
  const res = await request.get<unknown, { url: string }>(`/file/presign/${id}`)
  return res.url
}
