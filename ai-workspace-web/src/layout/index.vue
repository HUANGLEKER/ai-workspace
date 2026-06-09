<template>
  <div class="app-layout">
    <Sidebar :collapsed="sidebarCollapsed" @toggle="sidebarCollapsed = !sidebarCollapsed" />
    <div class="main-container">
      <Header />
      <main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

/**
 * 应用主布局组件
 *
 * 功能：
 * 1. 组合侧边栏与顶部 Header，构成整体页面框架
 * 2. 管理侧边栏折叠状态
 * 3. 挂载时补全用户信息，避免页面刷新后 Store 为空导致权限菜单丢失
 */
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import Sidebar from './components/Sidebar.vue'
import Header from './components/Header.vue'
import { useAuthStore } from '@/stores/auth'
import { getUserInfo } from '@/api/auth'

const authStore = useAuthStore()
const sidebarCollapsed = ref(false)

onMounted(async () => {
  // 路由守卫已确认 token 有效，但刷新后 Pinia Store 内存状态丢失
  // 此处按需补全，确保 Header 与侧边栏能正确显示用户名和 isAdmin 标记
  if (authStore.isLoggedIn && !authStore.userInfo) {
    try {
      const info = await getUserInfo()
      authStore.setUserInfo(info)
    } catch {}
  }
})
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.main-content {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-6);
  background: var(--bg-body);
  scroll-behavior: smooth;
}

.page-enter-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.page-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
