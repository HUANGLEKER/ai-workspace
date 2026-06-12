/**
 * Axios 实例封装
 *
 * 统一处理：
 * 1. JWT Bearer Token 注入
 * 2. 业务层响应解包（code=200 时透传 data）
 * 3. 401 先尝试静默刷新（/auth/refresh 轮换 token）并重放原请求，
 *    刷新失败才清除登录态跳转登录页
 * 4. 其他错误统一弹出 toast 提示
 *
 * 注意：后端错误约定为 HTTP 200 + body 内 code（common.Fail），
 * 因此 401 主要出现在成功回调的 res.code 上，而非 HTTP 状态码。
 */
import axios, { type InternalAxiosRequestConfig } from 'axios'
import { toast } from '@/components/ui/toast'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截：从 localStorage 读取 token 并写入 Authorization 头
request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

/** 标记已重试过的请求，防止刷新后仍 401 时无限循环 */
interface RetriableConfig extends InternalAxiosRequestConfig {
  _retried?: boolean
}

/** 并发 401 共享同一个刷新 Promise，避免同时发出多个 /auth/refresh（旧 token 已被轮换吊销会连环失败） */
let refreshing: Promise<boolean> | null = null

function tryRefreshToken(): Promise<boolean> {
  if (!refreshing) {
    // 用裸 axios 绕过本实例拦截器，防止刷新请求自身的 401 触发递归
    refreshing = axios
      .post('/api/auth/refresh', null, {
        headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }
      })
      .then(r => {
        const token = r.data?.code === 200 ? r.data?.data?.token : null
        if (token) {
          useAuthStore().setToken(token)
          return true
        }
        return false
      })
      .catch(() => false)
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

function redirectToLogin() {
  useAuthStore().logout()
  const current = router.currentRoute.value
  if (current.path !== '/login') {
    void router.push({ path: '/login', query: { redirect: current.fullPath } })
  }
}

/** 401 统一处理：先静默刷新重放一次，失败再跳登录 */
async function handleUnauthorized(config: RetriableConfig | undefined): Promise<unknown> {
  const isAuthPath = !!config?.url && /\/auth\/(login|refresh|logout)/.test(config.url)
  if (config && !config._retried && !isAuthPath && (await tryRefreshToken())) {
    config._retried = true
    return request(config) // 重放：请求拦截器会注入新 token
  }
  redirectToLogin()
  return Promise.reject(new Error('登录已过期'))
}

request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    // 后端 401 走 HTTP 200 + body code（common.Unauthorized）
    if (res.code === 401) {
      return handleUnauthorized(response.config as RetriableConfig)
    }
    toast.error(res.message || '操作失败')
    return Promise.reject(new Error(res.message || '操作失败'))
  },
  error => {
    if (error.response?.status === 401) {
      return handleUnauthorized(error.config as RetriableConfig)
    }
    const msg = error.response?.data?.message || error.message || '网络连接失败'
    toast.error(msg)
    return Promise.reject(error)
  }
)

export default request
