/**
 * 用户管理 API（仅管理员）
 *
 * 对应后端 SysUserController，全部接口以 @PreAuthorize("hasRole('ADMIN')") 保护。
 * 密码由后端 BCrypt 编码，更新时若不传 password 则保持不变。
 */
import request from './request'
import type { SysUser, PageResult } from '@/types'

export const pageUsers = (params: { page: number; size: number; username?: string }) =>
  request.get<unknown, PageResult<SysUser>>('/user/page', { params })

export const addUser = (data: SysUser) =>
  request.post<unknown, void>('/user/add', data)

export const updateUser = (data: SysUser) =>
  request.put<unknown, void>('/user/update', data)

export const deleteUser = (id: number) =>
  request.delete<unknown, void>(`/user/delete/${id}`)

export const toggleUserStatus = (id: number, status: number) =>
  request.put<unknown, void>('/user/status', { id, status })
