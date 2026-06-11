<template>
  <div class="pb-6">
    <div class="mb-6 flex items-start justify-between gap-4">
      <div>
        <h2 class="text-lg font-semibold text-zinc-800 dark:text-zinc-100">仪表盘</h2>
        <p class="mt-0.5 text-sm text-zinc-500 dark:text-zinc-400">{{ todayText }}</p>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-2 gap-4 lg:grid-cols-4">
      <div
        v-for="stat in stats"
        :key="stat.label"
        class="rounded-2xl border border-zinc-200/80 bg-white p-5 shadow-sm transition-all duration-200 ease-out hover:shadow-md dark:border-zinc-800 dark:bg-zinc-900"
      >
        <div class="flex items-center justify-between">
          <span class="text-sm text-zinc-500 dark:text-zinc-400">{{ stat.label }}</span>
          <component :is="stat.icon" class="h-4 w-4 text-zinc-400 dark:text-zinc-500" />
        </div>
        <div class="mt-3 text-3xl font-semibold tracking-tight text-zinc-800 dark:text-zinc-100">{{ stat.value }}</div>
      </div>
    </div>

    <div class="mt-4">
      <!-- 快速入口 -->
      <AppCard title="快速入口">
        <div class="grid grid-cols-3 gap-3 lg:grid-cols-6">
          <button
            v-for="action in quickActions"
            :key="action.label"
            class="flex flex-col items-center gap-2.5 rounded-xl border border-zinc-200/80 bg-white px-2 py-5 text-sm text-zinc-500 transition-all duration-200 ease-out hover:bg-zinc-50 hover:text-zinc-800 hover:shadow-sm dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100"
            @click="router.push(action.path)"
          >
            <div class="flex h-10 w-10 items-center justify-center rounded-xl bg-zinc-100/50 dark:bg-zinc-800">
              <component :is="action.icon" class="h-5 w-5 text-zinc-800 dark:text-zinc-100" />
            </div>
            {{ action.label }}
          </button>
        </div>
      </AppCard>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 仪表盘页：当前用户统计概览 + 快速入口 + 系统技术栈信息
 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  MessageSquare, BookOpen, FileText, Folder, Sparkles, Workflow, Cpu
} from 'lucide-vue-next'
import { getDashboardStats } from '@/api/dashboard'
import { AppCard } from '@/components/ui'

const router = useRouter()

const todayText = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric', month: 'long', day: 'numeric', weekday: 'long'
})

const stats = ref([
  { label: '今日对话', value: 0, icon: MessageSquare },
  { label: '知识库数量', value: 0, icon: BookOpen },
  { label: '文档总数', value: 0, icon: FileText },
  { label: '文件总数', value: 0, icon: Folder }
])

onMounted(async () => {
  try {
    const data = await getDashboardStats()
    stats.value[0].value = data.todaySessions
    stats.value[1].value = data.kbCount
    stats.value[2].value = data.docCount
    stats.value[3].value = data.fileCount
  } catch (err) {
    // 拉取失败时统计保持 0，记录便于排查
    console.error('[dashboard] 加载统计数据失败：', err)
  }
})

const quickActions = [
  { label: '新建对话', icon: MessageSquare, path: '/chat' },
  { label: '知识库管理', icon: BookOpen, path: '/knowledge/base' },
  { label: '上传文件', icon: Folder, path: '/file' },
  { label: '提示词中心', icon: Sparkles, path: '/prompt' },
  { label: '工作流', icon: Workflow, path: '/workflow' },
  { label: 'Agent', icon: Cpu, path: '/agent' }
]


</script>
