<template>
  <div class="pb-6">
    <div class="mb-6 flex items-start justify-between gap-4">
      <div>
        <h2 class="text-lg font-semibold text-zinc-800">仪表盘</h2>
        <p class="mt-0.5 text-sm text-zinc-500">{{ todayText }}</p>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-2 gap-4 lg:grid-cols-4">
      <div
        v-for="stat in stats"
        :key="stat.label"
        class="rounded-2xl border border-zinc-200/80 bg-white p-5 shadow-sm transition-all duration-200 ease-out hover:shadow-md"
      >
        <div class="flex items-center justify-between">
          <span class="text-sm text-zinc-500">{{ stat.label }}</span>
          <component :is="stat.icon" class="h-4 w-4 text-zinc-400" />
        </div>
        <div class="mt-3 text-3xl font-semibold tracking-tight text-zinc-800">{{ stat.value }}</div>
      </div>
    </div>

    <div class="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-3">
      <!-- 快速入口 -->
      <AppCard title="快速入口" class="lg:col-span-2">
        <div class="grid grid-cols-3 gap-3">
          <button
            v-for="action in quickActions"
            :key="action.label"
            class="flex flex-col items-center gap-2.5 rounded-xl border border-zinc-200/80 bg-white px-2 py-5 text-sm text-zinc-500 transition-all duration-200 ease-out hover:bg-zinc-50 hover:text-zinc-800 hover:shadow-sm"
            @click="router.push(action.path)"
          >
            <div class="flex h-10 w-10 items-center justify-center rounded-xl bg-zinc-100/50">
              <component :is="action.icon" class="h-5 w-5 text-zinc-800" />
            </div>
            {{ action.label }}
          </button>
        </div>
      </AppCard>

      <!-- 系统信息 -->
      <AppCard title="系统信息">
        <dl class="flex flex-col gap-3 text-sm">
          <div v-for="info in sysInfo" :key="info.label" class="flex items-center justify-between">
            <dt class="text-zinc-500">{{ info.label }}</dt>
            <dd class="text-zinc-800">{{ info.value }}</dd>
          </div>
        </dl>
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
  } catch {
    // stats stay at 0 on error
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

const sysInfo = [
  { label: '版本', value: 'v1.0.0' },
  { label: '前端框架', value: 'Vue 3 + Tailwind' },
  { label: '后端框架', value: 'Go + Gin' },
  { label: 'AI 框架', value: 'LangChain + LangGraph' },
  { label: '向量数据库', value: 'ChromaDB' }
]
</script>
