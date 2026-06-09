/**
 * Axios 实例封装
 *
 * 统一处理：
 * 1. JWT Bearer Token 注入
 * 2. 业务层响应解包（code=200 时透传 data）
 * 3. 401 自动清除 token 并跳转登录页
 * 4. 其他错误统一弹出 ElMessage 提示
 */
import axios from 'axios'

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
    ElMessage.error(res.message || '操作失败')
    return Promise.reject(new Error(res.message || '操作失败'))
  },
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      // 避免登录页本身触发 401 后陷入无限重定向
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
      return Promise.reject(error)
    }
    const msg = error.response?.data?.message || error.message || '网络连接失败'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default request
