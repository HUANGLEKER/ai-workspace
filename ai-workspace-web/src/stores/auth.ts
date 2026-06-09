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

export const useAuthStore = defineStore('auth', () => {
  // 初始化时从 localStorage 恢复 token，支持刷新后保持登录
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  // role_code 已含 'ROLE_' 前缀，与 Spring Security @PreAuthorize 保持一致
  const isAdmin = computed(() => userInfo.value?.roles?.includes('ROLE_ADMIN') ?? false)

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUserInfo(info: UserInfo) {
    userInfo.value = info
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
  }

  return { token, userInfo, isLoggedIn, isAdmin, setToken, setUserInfo, logout }
})
