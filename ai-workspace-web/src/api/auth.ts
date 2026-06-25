/**
 * 认证相关 API
 *
 * 对应后端 /api/auth/* 端点，负责登录、登出及获取当前用户信息（含角色列表）。
 * 路由守卫在保护页面直接导航或刷新时会调用 getUserInfo 以补全 Store 内的角色数据。
 */
import request from './request'
import type {
  LoginRequest,
  LoginResponse,
  UserInfo,
  SlideCaptchaInit,
  SlideCaptchaVerifyResult,
} from '@/types'

export const login = (data: LoginRequest) =>
  // silent：登录失败由登录页弹出提示框处理，不走拦截器的全局 toast
  request.post<unknown, LoginResponse>('/auth/login', data, { silent: true } as never)

// 获取一张滑块拼图（登录前的人机验证）
export const initSlideCaptcha = () =>
  request.get<unknown, SlideCaptchaInit>('/auth/captcha/slide/init')

// 校验滑块落点；成功返回一次性 captchaToken。silent：失败在组件内处理，不弹全局 toast
export const verifySlideCaptcha = (data: { captchaId: string; x: number }) =>
  request.post<unknown, SlideCaptchaVerifyResult>('/auth/captcha/slide/verify', data, {
    silent: true,
  } as never)

export const logout = () =>
  request.post<unknown, void>('/auth/logout')

export const getUserInfo = () =>
  request.get<unknown, UserInfo>('/auth/info')

export const updatePassword = (data: { oldPassword: string; newPassword: string }) =>
  request.put<unknown, void>('/auth/password', data)

// 修改当前用户资料（昵称、邮箱）；头像走 AppUpload 直传 /api/auth/avatar
export const updateProfile = (data: { nickname: string; email: string }) =>
  request.put<unknown, void>('/auth/profile', data)
