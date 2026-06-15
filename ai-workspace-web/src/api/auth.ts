/**
 * 认证相关 API
 *
 * 对应后端 /api/auth/* 端点，负责登录、登出及获取当前用户信息（含角色列表）。
 * 路由守卫在保护页面直接导航或刷新时会调用 getUserInfo 以补全 Store 内的角色数据。
 */
import request from './request'
import type { LoginRequest, LoginResponse, UserInfo } from '@/types'

export const login = (data: LoginRequest) =>
  // silent：登录失败由登录页弹出提示框处理，不走拦截器的全局 toast
  request.post<unknown, LoginResponse>('/auth/login', data, { silent: true } as never)

export const logout = () =>
  request.post<unknown, void>('/auth/logout')

export const getUserInfo = () =>
  request.get<unknown, UserInfo>('/auth/info')

export const updatePassword = (data: { oldPassword: string; newPassword: string }) =>
  request.put<unknown, void>('/auth/password', data)

// 修改当前用户资料（昵称、邮箱）；头像走 AppUpload 直传 /api/auth/avatar
export const updateProfile = (data: { nickname: string; email: string }) =>
  request.put<unknown, void>('/auth/profile', data)
