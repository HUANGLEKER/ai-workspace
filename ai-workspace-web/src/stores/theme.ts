/**
 * 主题状态 Store
 *
 * 支持三种偏好：light / dark / system（跟随系统）。
 * - 偏好持久化到 localStorage（key=theme），刷新后保留
 * - 实际生效的暗色由 documentElement 上的 .dark class 决定，apply() 统一负责切换
 * - system 模式下监听 prefers-color-scheme 变化实时跟随
 *
 * 首屏防闪烁（FOUC）由 index.html 内联脚本在应用挂载前先行设置 .dark，这里与之保持一致。
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export type ThemePreference = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'theme'
const media = window.matchMedia('(prefers-color-scheme: dark)')

function systemPrefersDark() {
  return media.matches
}

export const useThemeStore = defineStore('theme', () => {
  const preference = ref<ThemePreference>(
    (localStorage.getItem(STORAGE_KEY) as ThemePreference) || 'system'
  )

  // 当前是否处于暗色：显式 dark，或 system 且系统为暗色
  const isDark = computed(() =>
    preference.value === 'dark' || (preference.value === 'system' && systemPrefersDark())
  )

  function apply() {
    document.documentElement.classList.toggle('dark', isDark.value)
  }

  function setPreference(pref: ThemePreference) {
    preference.value = pref
    localStorage.setItem(STORAGE_KEY, pref)
    apply()
  }

  // 在 light → dark → system 间循环（供顶栏按钮使用）
  function cycle() {
    const order: ThemePreference[] = ['light', 'dark', 'system']
    const next = order[(order.indexOf(preference.value) + 1) % order.length]
    setPreference(next)
  }

  // system 模式下跟随系统变化；非 system 模式忽略
  media.addEventListener('change', () => {
    if (preference.value === 'system') apply()
  })

  // Store 初始化即应用一次，确保与持久化偏好一致
  apply()

  return { preference, isDark, setPreference, cycle, apply }
})
