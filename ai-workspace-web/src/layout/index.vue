<template>
  <div class="flex h-screen overflow-hidden bg-white text-zinc-800 dark:bg-zinc-950 dark:text-zinc-100">
    <Sidebar :collapsed="sidebarCollapsed" @toggle="sidebarCollapsed = !sidebarCollapsed" />
    <div class="flex flex-1 flex-col overflow-hidden">
      <Header />
      <router-view v-slot="{ Component, route }">
        <main
          class="flex flex-1 flex-col overflow-hidden scroll-smooth"
          :class="route.meta.fullPage ? 'bg-white dark:bg-zinc-900' : 'overflow-y-auto bg-zinc-50 p-6 dark:bg-zinc-950'"
        >
          <transition
            mode="out-in"
            enter-active-class="transition-all duration-200 ease-out"
            enter-from-class="opacity-0 translate-y-2"
            leave-active-class="transition-all duration-150 ease-out"
            leave-to-class="opacity-0 -translate-y-1"
          >
            <component :is="Component" :key="route.path" />
          </transition>
        </main>
      </router-view>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 应用主布局：Sidebar + Header + 内容区。
 * 用户信息由路由守卫（router.beforeEach）统一拉取并写入 Store，
 * 布局层不再重复拉取，避免逻辑分散与冗余请求。
 */
import { ref } from 'vue'
import Sidebar from './components/Sidebar.vue'
import Header from './components/Header.vue'

const sidebarCollapsed = ref(false)
</script>
