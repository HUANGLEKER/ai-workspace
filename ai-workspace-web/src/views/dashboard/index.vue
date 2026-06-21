<template>
  <PageShell gap="lg" scroll>
    <PageHeader
      title="个人 AI 工作台"
      :icon="LayoutDashboard"
      :description="todayText + '，从这里进入对话、知识库、自动化和资源管理。'"
    >
      <template #actions>
        <AppButton variant="primary" :icon="MessageSquare" @click="router.push('/chat')">新建对话</AppButton>
        <AppButton :icon="FileSearch" @click="router.push('/knowledge/rag')">知识库问答</AppButton>
      </template>
    </PageHeader>

    <div class="grid grid-cols-2 gap-4 lg:grid-cols-3 xl:grid-cols-6">
      <MetricCard
        v-for="stat in stats"
        :key="stat.label"
        :label="stat.label"
        :value="stat.value"
        :hint="stat.hint"
        :icon="stat.icon"
      />
    </div>

    <div class="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
      <AppCard title="快捷入口">
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">
          <ResourceCard
            v-for="action in quickActions"
            :key="action.label"
            :title="action.label"
            :description="action.description"
            :icon="action.icon"
          >
            <template #actions>
              <AppButton size="sm" :icon="ArrowRight" @click="router.push(action.path)">进入</AppButton>
            </template>
          </ResourceCard>
        </div>
      </AppCard>

      <div class="flex flex-col gap-4">
        <AppCard title="推荐工作流">
          <div class="space-y-3">
            <button
              v-for="item in recommendations"
              :key="item.title"
              class="flex w-full items-start gap-3 rounded-lg p-2 text-left transition-all duration-200 ease-out hover:bg-zinc-50 dark:hover:bg-zinc-800"
              @click="router.push(item.path)"
            >
              <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-200">
                <component :is="item.icon" class="h-4 w-4" />
              </div>
              <div class="min-w-0">
                <div class="truncate text-sm font-medium text-zinc-900 dark:text-zinc-100">{{ item.title }}</div>
                <p class="mt-0.5 line-clamp-2 text-xs leading-5 text-zinc-500 dark:text-zinc-400">{{ item.description }}</p>
              </div>
            </button>
          </div>
        </AppCard>

        <AppCard title="运行状态">
          <div class="space-y-3 text-sm">
            <div class="flex items-center justify-between">
              <span class="text-zinc-500 dark:text-zinc-400">前端</span>
              <span class="font-medium text-emerald-600 dark:text-emerald-400">可用</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-zinc-500 dark:text-zinc-400">权限</span>
              <span class="font-medium text-zinc-900 dark:text-zinc-100">{{ authStore.isAdmin ? '管理员' : '普通用户' }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-zinc-500 dark:text-zinc-400">主题</span>
              <span class="font-medium text-zinc-900 dark:text-zinc-100">{{ themeLabel }}</span>
            </div>
          </div>
        </AppCard>
      </div>
    </div>
  </PageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  BookOpen,
  Cpu,
  FileSearch,
  FileText,
  Folder,
  LayoutDashboard,
  MessageSquare,
  Sparkles,
  Workflow,
  Zap
} from 'lucide-vue-next'
import { getDashboardStats } from '@/api/dashboard'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { AppButton, AppCard, MetricCard, PageHeader, PageShell, ResourceCard } from '@/components/ui'

const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()

const todayText = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'long'
})

const themeLabel = computed(() =>
  themeStore.preference === 'light' ? '亮色' : themeStore.preference === 'dark' ? '暗色' : '跟随系统'
)

const stats = ref<{ label: string; value: number | string; hint: string; icon: Component }[]>([
  { label: '今日对话', value: 0, hint: '当天创建的会话', icon: MessageSquare },
  { label: '知识库', value: 0, hint: '个人可用知识库', icon: BookOpen },
  { label: '文档', value: 0, hint: '知识库文档总数', icon: FileText },
  { label: '文件', value: 0, hint: '文件中心资源', icon: Folder },
  { label: '今日 Token', value: 0, hint: '当天消耗估算', icon: Zap },
  { label: '累计 Token', value: 0, hint: '历史消耗统计', icon: Zap }
])

function fmtTokens(n: number): string {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'k'
  return String(n)
}

onMounted(async () => {
  try {
    const data = await getDashboardStats()
    stats.value[0].value = data.todaySessions
    stats.value[1].value = data.kbCount
    stats.value[2].value = data.docCount
    stats.value[3].value = data.fileCount
    stats.value[4].value = fmtTokens(data.todayTokens ?? 0)
    stats.value[5].value = fmtTokens(data.tokenTotal ?? 0)
  } catch (err) {
    console.error('[dashboard] failed to load stats', err)
  }
})

const quickActions = [
  { label: 'AI 对话', description: '直接进入多模型聊天、联网搜索和 Artifact 预览。', icon: MessageSquare, path: '/chat' },
  { label: '知识库问答', description: '基于已上传文档进行 RAG 问答和来源追踪。', icon: FileSearch, path: '/knowledge/rag' },
  { label: '知识库管理', description: '创建知识库、管理文档和重建嵌入索引。', icon: BookOpen, path: '/knowledge/base' },
  { label: '提示词中心', description: '沉淀常用提示词，并插入到 Chat 或 RAG 会话。', icon: Sparkles, path: '/prompt' },
  { label: '工作流', description: '用画布编排 LLM、HTTP、搜索和结束节点。', icon: Workflow, path: '/workflow' },
  { label: 'Agent', description: '配置工具调用能力，运行面向任务的智能体。', icon: Cpu, path: '/agent' }
]

const recommendations = [
  { title: '先上传文件，再构建知识库', description: '适合把本地资料快速变成可问答的个人知识源。', icon: Folder, path: '/file' },
  { title: '沉淀提示词模板', description: '把高频工作提示词放入提示词中心，减少重复输入。', icon: Sparkles, path: '/prompt' },
  { title: '编排自动化流程', description: '将固定步骤沉淀为工作流，适合资料处理和批量任务。', icon: Workflow, path: '/workflow' }
]
</script>
