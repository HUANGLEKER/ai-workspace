<template>
  <aside
    class="relative flex h-full shrink-0 flex-col border-r border-zinc-200/80 bg-zinc-50 transition-all duration-200 ease-out dark:border-zinc-800 dark:bg-zinc-900"
    :class="collapsed ? 'w-[72px]' : 'w-[260px]'"
  >
    <!-- Logo -->
    <div class="flex h-14 items-center gap-2.5 border-b border-zinc-200/80 px-4 dark:border-zinc-800">
      <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-zinc-900 dark:bg-zinc-100">
        <Bot class="h-4.5 w-4.5 text-white dark:text-zinc-900" />
      </div>
      <span v-if="!collapsed" class="truncate text-sm font-semibold text-zinc-800 dark:text-zinc-100">AI Workspace</span>
    </div>

    <!-- 导航 -->
    <nav class="flex-1 overflow-y-auto overflow-x-hidden px-3 py-3">
      <template v-for="group in visibleGroups" :key="group.label">
        <div
          v-if="group.label && !collapsed"
          class="mb-1 mt-4 px-3 text-xs font-medium text-zinc-400 first:mt-0 dark:text-zinc-500"
        >
          {{ group.label }}
        </div>
        <div v-else-if="group.label && collapsed" class="my-3 h-px bg-zinc-200/80 dark:bg-zinc-800" />

        <router-link
          v-for="item in group.items"
          :key="item.path"
          :to="item.path"
          class="mb-0.5 flex items-center gap-2.5 rounded-xl px-3 py-2 text-sm transition-all duration-200 ease-out"
          :class="[
            isActive(item.path)
              ? 'bg-zinc-200/80 text-zinc-900 font-medium dark:bg-zinc-800 dark:text-zinc-100'
              : 'text-zinc-500 hover:bg-zinc-100/50 hover:text-zinc-800 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100',
            collapsed ? 'justify-center px-0' : ''
          ]"
        >
          <AppTooltip v-if="collapsed" :content="item.title" side="right">
            <component :is="item.icon" class="h-4 w-4 shrink-0" />
          </AppTooltip>
          <component :is="item.icon" v-else class="h-4 w-4 shrink-0" />
          <span v-if="!collapsed" class="truncate">{{ item.title }}</span>
        </router-link>
      </template>
    </nav>

    <!-- 折叠按钮 -->
    <button
      class="flex h-11 items-center justify-center border-t border-zinc-200/80 text-zinc-500 transition-all duration-200 ease-out hover:bg-zinc-100/50 hover:text-zinc-800 dark:border-zinc-800 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100"
      @click="$emit('toggle')"
    >
      <PanelLeft class="h-4 w-4" />
    </button>
  </aside>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useRoute } from 'vue-router'
import {
  Bot, PanelLeft, LayoutDashboard, MessageSquare, BookOpen, FileSearch,
  Folder, Sparkles, Workflow, Cpu, Wrench, Plug, Activity, Users, Boxes, Timer
} from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { AppTooltip } from '@/components/ui'

interface NavItem {
  path: string
  title: string
  icon: Component
}

defineProps<{ collapsed: boolean }>()
defineEmits<{ toggle: [] }>()

const route = useRoute()
const authStore = useAuthStore()

const groups: { label: string; admin?: boolean; items: NavItem[] }[] = [
  {
    label: '',
    items: [
      { path: '/dashboard', title: '仪表盘', icon: LayoutDashboard },
      { path: '/chat', title: 'AI 对话', icon: MessageSquare }
    ]
  },
  {
    label: '知识库',
    items: [
      { path: '/knowledge/base', title: '知识库管理', icon: BookOpen },
      { path: '/knowledge/rag', title: '知识库问答', icon: FileSearch }
    ]
  },
  {
    label: '工作台',
    items: [
      { path: '/file', title: '文件中心', icon: Folder },
      { path: '/prompt', title: '提示词中心', icon: Sparkles },
      { path: '/workflow', title: '工作流', icon: Workflow },
      { path: '/agent', title: 'Agent', icon: Cpu },
      { path: '/tool', title: '工具中心', icon: Wrench },
      { path: '/mcp', title: 'MCP 服务器', icon: Plug }
    ]
  },
  {
    label: '系统',
    admin: true,
    items: [
      { path: '/monitor', title: '监控', icon: Activity },
      { path: '/system/user', title: '用户管理', icon: Users },
      { path: '/system/model', title: '模型管理', icon: Boxes },
      { path: '/system/job', title: '定时任务', icon: Timer }
    ]
  }
]

// 管理员分组对普通用户隐藏，防止暴露系统入口
const visibleGroups = computed(() => groups.filter((g) => !g.admin || authStore.isAdmin))

const isActive = (path: string) => route.path === path || route.path.startsWith(path + '/')
</script>
