/**
 * 认证相关 API
 *
 * 对应后端 /api/auth/* 端点，负责登录、登出及获取当前用户信息（含角色列表）。
 * 路由守卫在保护页面直接导航或刷新时会调用 getUserInfo 以补全 Store 内的角色数据。
 */
import request from './request'
import type { LoginRequest, LoginResponse, UserInfo } from '@/types'

export const login = (data: LoginRequest) =>
  request.post<unknown, LoginResponse>('/auth/login', data)

export const logout = () =>
  request.post<unknown, void>('/auth/logout')

export const getUserInfo = () =>
  request.get<unknown, UserInfo>('/auth/info')
