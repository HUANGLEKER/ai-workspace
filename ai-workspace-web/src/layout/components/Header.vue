<template>
  <header class="sticky top-0 z-50 flex h-14 shrink-0 items-center justify-between border-b border-zinc-200/80 bg-white px-6">
    <!-- 面包屑 -->
    <div class="flex items-center gap-1.5 text-sm">
      <router-link to="/" class="text-zinc-500 transition-all duration-200 ease-out hover:text-zinc-800">首页</router-link>
      <ChevronRight class="h-3.5 w-3.5 text-zinc-400" />
      <span class="text-zinc-800">{{ currentTitle }}</span>
    </div>

    <div class="flex items-center gap-2">
      <AppTooltip content="帮助文档" side="bottom">
        <button class="rounded-xl p-2 text-zinc-500 transition-all duration-200 ease-out hover:bg-zinc-100/50 hover:text-zinc-800">
          <CircleHelp class="h-4 w-4" />
        </button>
      </AppTooltip>

      <AppDropdown :items="menuItems" @select="handleCommand">
        <button
          class="flex items-center gap-2 rounded-xl px-2 py-1.5 transition-all duration-200 ease-out hover:bg-zinc-100/50 focus:outline-none"
        >
          <AppAvatar :icon="User" variant="dark" size="sm" />
          <span class="max-w-[100px] truncate text-sm text-zinc-800">
            {{ authStore.userInfo?.nickname || authStore.userInfo?.username || '用户' }}
          </span>
          <ChevronDown class="h-3.5 w-3.5 text-zinc-400" />
        </button>
      </AppDropdown>
    </div>
  </header>
</template>

<script setup lang="ts">
/**
 * 顶部 Header：面包屑 + 用户菜单（个人信息 / 修改密码 / 退出登录）
 * 退出登录先调接口使服务端 token 失效，再清除本地 Store。
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChevronRight, ChevronDown, CircleHelp, User, Lock, LogOut } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { logout } from '@/api/auth'
import { AppDropdown, AppTooltip, AppAvatar, confirm, alertBox, type DropdownItem } from '@/components/ui'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const currentTitle = computed(() => (route.meta.title as string) || '')

const menuItems: DropdownItem[] = [
  { key: 'profile', label: '个人信息', icon: User },
  { key: 'password', label: '修改密码', icon: Lock },
  { key: 'logout', label: '退出登录', icon: LogOut, danger: true, divided: true }
]

const handleCommand = async (cmd: string) => {
  if (cmd === 'logout') {
    const ok = await confirm({
      title: '退出确认',
      message: '确定要退出登录吗？',
      confirmText: '确定退出',
      danger: true
    })
    if (!ok) return
    try { await logout() } catch {}
    authStore.logout()
    router.push('/login')
  } else if (cmd === 'profile') {
    alertBox({ message: '个人信息功能即将上线' })
  } else if (cmd === 'password') {
    alertBox({ message: '修改密码功能即将上线' })
  }
}
</script>
