/**
 * 认证状态 Store
 *
 * 持久化 token 到 localStorage，避免刷新后丢失登录态。
 * userInfo（含 roles）在每次进入受保护路由时由路由守卫按需加载，
 * 不在 Store 初始化时主动拉取——因为页面首次加载时路由守卫必然触发。
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo } from '@/types'
import { getUserInfo } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  // 初始化时从 localStorage 恢复 token，支持刷新后保持登录
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  // 后端 /auth/info 直接返回 isAdmin 布尔值，无需前端自行判断角色
  const isAdmin = computed(() => userInfo.value?.isAdmin ?? false)

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUserInfo(info: UserInfo) {
    userInfo.value = info
  }

  // 收敛用户信息拉取逻辑：路由守卫与布局组件统一调用，避免重复实现
  // 已有 userInfo 时直接跳过；token 失效时由 request 拦截器统一处理 401 跳转
  async function fetchUserInfo() {
    if (userInfo.value) return
    const info = await getUserInfo()
    userInfo.value = info
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
  }

  return { token, userInfo, isLoggedIn, isAdmin, setToken, setUserInfo, fetchUserInfo, logout }
})
