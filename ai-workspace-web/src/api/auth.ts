import request from './request'
import type { LoginRequest, LoginResponse, UserInfo } from '@/types'

export const login = (data: LoginRequest) =>
  request.post<unknown, LoginResponse>('/auth/login', data)

export const logout = () =>
  request.post<unknown, void>('/auth/logout')

export const getUserInfo = () =>
  request.get<unknown, UserInfo>('/auth/info')
