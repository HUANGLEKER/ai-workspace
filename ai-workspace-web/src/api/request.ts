/**
 * Axios 实例封装
 *
 * 统一处理：
 * 1. JWT Bearer Token 注入
 * 2. 业务层响应解包（code=200 时透传 data）
 * 3. 401 自动清除 token 并跳转登录页
 * 4. 其他错误统一弹出 toast 提示
 */
import axios from 'axios'
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

request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    toast.error(res.message || '操作失败')
    return Promise.reject(new Error(res.message || '操作失败'))
  },
  error => {
    if (error.response?.status === 401) {
      // 清空鉴权态（同时同步 Store 与 localStorage），再用 router 跳转避免整页刷新
      useAuthStore().logout()
      const current = router.currentRoute.value
      if (current.path !== '/login') {
        void router.push({ path: '/login', query: { redirect: current.fullPath } })
      }
      return Promise.reject(error)
    }
    const msg = error.response?.data?.message || error.message || '网络连接失败'
    toast.error(msg)
    return Promise.reject(error)
  }
)

export default request
