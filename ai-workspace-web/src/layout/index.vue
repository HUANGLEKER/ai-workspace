<template>
  <div class="flex h-screen overflow-hidden bg-canvas text-ink">
    <!-- 移动端抽屉遮罩：仅 <md 显示，点击关闭侧栏 -->
    <div
      v-if="mobileNavOpen"
      class="fixed inset-0 z-[6999] bg-zinc-950/40 md:hidden"
      @click="mobileNavOpen = false"
    />
    <Sidebar
      :collapsed="sidebarCollapsed"
      :mobile-open="mobileNavOpen"
      @toggle="sidebarCollapsed = !sidebarCollapsed"
    />
    <div class="flex flex-1 flex-col overflow-hidden">
      <Header @toggle-nav="mobileNavOpen = !mobileNavOpen" />
      <router-view v-slot="{ Component, route }">
        <main
          class="flex flex-1 flex-col overflow-hidden scroll-smooth"
          :class="route.meta.fullPage ? 'bg-surface' : 'overflow-y-auto bg-canvas p-6'"
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
    <!-- 强制改密引导（P3-6）：默认密码未改时弹出，不可绕过 -->
    <ForcePasswordChange />
  </div>
</template>

<script setup lang="ts">
/**
 * 应用主布局：Sidebar + Header + 内容区。
 * 用户信息由路由守卫（router.beforeEach）统一拉取并写入 Store，
 * 布局层不再重复拉取，避免逻辑分散与冗余请求。
 */
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import Sidebar from './components/Sidebar.vue'
import Header from './components/Header.vue'
import ForcePasswordChange from './components/ForcePasswordChange.vue'

const sidebarCollapsed = ref(false)
// 移动端抽屉式侧栏开关；路由切换后自动收起，避免跳转后遮罩残留
const mobileNavOpen = ref(false)
const route = useRoute()
watch(
  () => route.path,
  () => {
    mobileNavOpen.value = false
  }
)
</script>
