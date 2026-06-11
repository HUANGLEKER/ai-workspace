<template>
  <div class="pb-6">
    <div class="mb-4 flex items-start justify-between gap-4">
      <h2 class="text-lg font-semibold text-zinc-800 dark:text-zinc-100">系统监控</h2>
      <div class="flex items-center gap-3">
        <span v-if="lastUpdated" class="text-sm text-zinc-500 dark:text-zinc-400">更新于 {{ lastUpdated }}</span>
        <AppButton :icon="RefreshCw" :loading="loading" @click="refresh">刷新</AppButton>
        <AppButton :variant="autoRefresh ? 'primary' : 'secondary'" @click="toggleAuto">
          {{ autoRefresh ? '关闭自动刷新' : '自动刷新 (5s)' }}
        </AppButton>
      </div>
    </div>

    <!-- 依赖服务健康 -->
    <AppCard title="依赖服务" class="mb-4">
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div v-for="svc in health" :key="svc.name" class="flex items-start gap-3 rounded-xl border border-zinc-200/80 dark:border-zinc-800 p-4">
          <span
            class="mt-1 h-2.5 w-2.5 shrink-0 rounded-full"
            :class="svc.status === 'UP' ? 'bg-emerald-500' : 'bg-red-500'"
          />
          <div class="min-w-0">
            <div class="text-sm font-semibold text-zinc-800 dark:text-zinc-100">{{ svc.name }}</div>
            <div class="mt-1 flex items-center gap-2">
              <AppTag :variant="svc.status === 'UP' ? 'success' : 'danger'">{{ svc.status }}</AppTag>
              <span v-if="svc.status === 'UP'" class="text-xs text-emerald-600">{{ svc.latency }} ms</span>
              <span v-else class="text-xs text-red-500">{{ svc.message }}</span>
            </div>
            <div class="mt-1 break-all text-xs text-zinc-400 dark:text-zinc-500">{{ svc.target }}</div>
          </div>
        </div>
      </div>
    </AppCard>

    <!-- 资源使用率 -->
    <div v-if="server" class="mb-4 grid grid-cols-1 gap-4 sm:grid-cols-3">
      <AppCard title="CPU">
        <UsageBar :percent="pct(server.cpu.usedPercent)" />
        <div class="mt-3 flex flex-col gap-1 text-sm text-zinc-500 dark:text-zinc-400">
          <span>系统使用率 {{ fmtPct(server.cpu.usedPercent) }}</span>
          <span>进程使用率 {{ fmtPct(server.cpu.procUsedPercent) }}</span>
          <span>逻辑核心 {{ server.cpu.cores }}</span>
        </div>
      </AppCard>
      <AppCard title="物理内存">
        <UsageBar :percent="pct(server.memory.usedPercent)" />
        <div class="mt-3 text-sm text-zinc-500 dark:text-zinc-400">
          {{ fmtBytes(server.memory.used) }} / {{ fmtBytes(server.memory.total) }}
        </div>
      </AppCard>
      <AppCard title="进程堆内存">
        <UsageBar :percent="pct(server.runtime.usedPercent)" />
        <div class="mt-3 text-sm text-zinc-500 dark:text-zinc-400">
          {{ fmtBytes(server.runtime.heapAlloc) }} / {{ fmtBytes(server.runtime.heapSys) }}
        </div>
      </AppCard>
    </div>

    <!-- 磁盘 + 运行环境 -->
    <div v-if="server" class="grid grid-cols-1 gap-4 lg:grid-cols-5">
      <AppCard title="磁盘" class="lg:col-span-3">
        <div class="flex flex-col gap-4">
          <div v-for="disk in server.disks" :key="disk.path">
            <div class="mb-1.5 flex items-center justify-between text-sm">
              <span class="text-zinc-800 dark:text-zinc-100">{{ disk.path }}</span>
              <span class="text-zinc-500 dark:text-zinc-400">{{ fmtBytes(disk.used) }} / {{ fmtBytes(disk.total) }}</span>
            </div>
            <UsageBar :percent="pct(disk.usedPercent)" />
          </div>
        </div>
      </AppCard>
      <AppCard title="运行环境" class="lg:col-span-2">
        <dl class="flex flex-col gap-3 text-sm">
          <div class="flex items-center justify-between gap-4">
            <dt class="shrink-0 text-zinc-500 dark:text-zinc-400">操作系统</dt>
            <dd class="text-right text-zinc-800 dark:text-zinc-100">{{ server.os.goos }} ({{ server.os.goarch }})</dd>
          </div>
          <div class="flex items-center justify-between gap-4">
            <dt class="shrink-0 text-zinc-500 dark:text-zinc-400">Go 协程数</dt>
            <dd class="text-right text-zinc-800 dark:text-zinc-100">{{ server.runtime.numGoroutine }}</dd>
          </div>
          <div class="flex items-center justify-between gap-4">
            <dt class="shrink-0 text-zinc-500 dark:text-zinc-400">Go 版本</dt>
            <dd class="text-right text-zinc-800 dark:text-zinc-100">{{ server.runtime.version }}</dd>
          </div>
        </dl>
      </AppCard>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 系统监控页（仅管理员）：依赖服务（Redis/FastAPI/MinIO）健康状态、
 * CPU/内存/进程堆使用率、磁盘与运行环境信息；支持手动与 5s 自动刷新。
 */
import { ref, onMounted, onUnmounted } from 'vue'
import { RefreshCw } from 'lucide-vue-next'
import {
  getServerInfo,
  getServiceHealth,
  type ServerInfo,
  type ServiceHealth
} from '@/api/monitor'
import { AppButton, AppCard, AppTag } from '@/components/ui'
import UsageBar from './UsageBar.vue'



const server = ref<ServerInfo | null>(null)
const health = ref<ServiceHealth[]>([])
const loading = ref(false)
const lastUpdated = ref('')
const autoRefresh = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

const refresh = async () => {
  loading.value = true
  try {
    const [s, healthData] = await Promise.all([getServerInfo(), getServiceHealth()])
    server.value = s
    health.value = healthData
    lastUpdated.value = new Date().toLocaleTimeString('zh-CN')
  } finally {
    loading.value = false
  }
}

const toggleAuto = () => {
  autoRefresh.value = !autoRefresh.value
  if (autoRefresh.value) {
    timer = setInterval(refresh, 5000)
  } else if (timer) {
    clearInterval(timer)
    timer = null
  }
}

onMounted(refresh)
onUnmounted(() => {
  if (timer) clearInterval(timer)
})

// 指标不可用时后端返回 -1，前端统一转为 0 避免进度条异常
const pct = (v: number) => (v < 0 ? 0 : Math.round(v))
const fmtPct = (v: number) => (v < 0 ? 'N/A' : v.toFixed(1) + '%')

const fmtBytes = (bytes: number) => {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(1) + ' ' + units[i]
}

</script>
