<template>
  <div class="flex h-screen overflow-hidden bg-white text-zinc-800">
    <Sidebar :collapsed="sidebarCollapsed" @toggle="sidebarCollapsed = !sidebarCollapsed" />
    <div class="flex flex-1 flex-col overflow-hidden">
      <Header />
      <main class="flex-1 overflow-y-auto scroll-smooth bg-zinc-50 p-6">
        <router-view v-slot="{ Component }">
          <transition
            mode="out-in"
            enter-active-class="transition-all duration-200 ease-out"
            enter-from-class="opacity-0 translate-y-2"
            leave-active-class="transition-all duration-150 ease-out"
            leave-to-class="opacity-0 -translate-y-1"
          >
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 应用主布局：Sidebar + Header + 内容区。
 * 挂载时补全用户信息，避免页面刷新后 Store 为空导致权限菜单丢失。
 */
import { ref, onMounted } from 'vue'
import Sidebar from './components/Sidebar.vue'
import Header from './components/Header.vue'
import { useAuthStore } from '@/stores/auth'
import { getUserInfo } from '@/api/auth'

const authStore = useAuthStore()
const sidebarCollapsed = ref(false)

onMounted(async () => {
  if (authStore.isLoggedIn && !authStore.userInfo) {
    try {
      const info = await getUserInfo()
      authStore.setUserInfo(info)
    } catch {}
  }
})
</script>
