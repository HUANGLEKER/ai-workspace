<template>
  <div class="pb-6">
    <div class="mb-4 flex items-start justify-between gap-4">
      <h2 class="text-lg font-semibold text-zinc-800">系统监控</h2>
      <div class="flex items-center gap-3">
        <span v-if="lastUpdated" class="text-sm text-zinc-500">更新于 {{ lastUpdated }}</span>
        <AppButton :icon="RefreshCw" :loading="loading" @click="refresh">刷新</AppButton>
        <AppButton :variant="autoRefresh ? 'primary' : 'secondary'" @click="toggleAuto">
          {{ autoRefresh ? '关闭自动刷新' : '自动刷新 (5s)' }}
        </AppButton>
      </div>
    </div>

    <!-- 依赖服务健康 -->
    <AppCard title="依赖服务" class="mb-4">
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div v-for="svc in health" :key="svc.name" class="flex items-start gap-3 rounded-xl border border-zinc-200/80 p-4">
          <span
            class="mt-1 h-2.5 w-2.5 shrink-0 rounded-full"
            :class="svc.status === 'UP' ? 'bg-emerald-500' : 'bg-red-500'"
          />
          <div class="min-w-0">
            <div class="text-sm font-semibold text-zinc-800">{{ svc.name }}</div>
            <div class="mt-1 flex items-center gap-2">
              <AppTag :variant="svc.status === 'UP' ? 'success' : 'danger'">{{ svc.status }}</AppTag>
              <span v-if="svc.status === 'UP'" class="text-xs text-emerald-600">{{ svc.latencyMs }} ms</span>
              <span v-else class="text-xs text-red-500">{{ svc.error }}</span>
            </div>
            <div class="mt-1 break-all text-xs text-zinc-400">{{ svc.target }}</div>
          </div>
        </div>
      </div>
    </AppCard>

    <!-- 资源使用率 -->
    <div v-if="server" class="mb-4 grid grid-cols-1 gap-4 sm:grid-cols-3">
      <AppCard title="CPU">
        <UsageBar :percent="pct(server.cpu.sysUsedPercent)" />
        <div class="mt-3 flex flex-col gap-1 text-sm text-zinc-500">
          <span>系统使用率 {{ fmtPct(server.cpu.sysUsedPercent) }}</span>
          <span>进程使用率 {{ fmtPct(server.cpu.procUsedPercent) }}</span>
          <span>逻辑核心 {{ server.cpu.cores }}</span>
        </div>
      </AppCard>
      <AppCard title="物理内存">
        <UsageBar :percent="pct(server.memory.usedPercent)" />
        <div class="mt-3 text-sm text-zinc-500">
          {{ fmtBytes(server.memory.used) }} / {{ fmtBytes(server.memory.total) }}
        </div>
      </AppCard>
      <AppCard title="进程堆内存">
        <UsageBar :percent="pct(server.jvm.usedPercent)" />
        <div class="mt-3 text-sm text-zinc-500">
          {{ fmtBytes(server.jvm.used) }} / {{ fmtBytes(server.jvm.max) }}
        </div>
      </AppCard>
    </div>

    <!-- 磁盘 + 运行环境 -->
    <div v-if="server" class="grid grid-cols-1 gap-4 lg:grid-cols-5">
      <AppCard title="磁盘" class="lg:col-span-3">
        <div class="flex flex-col gap-4">
          <div v-for="disk in server.disks" :key="disk.path">
            <div class="mb-1.5 flex items-center justify-between text-sm">
              <span class="text-zinc-800">{{ disk.path }}</span>
              <span class="text-zinc-500">{{ fmtBytes(disk.used) }} / {{ fmtBytes(disk.total) }}</span>
            </div>
            <UsageBar :percent="pct(disk.usedPercent)" />
          </div>
        </div>
      </AppCard>
      <AppCard title="运行环境" class="lg:col-span-2">
        <dl class="flex flex-col gap-3 text-sm">
          <div class="flex items-center justify-between gap-4">
            <dt class="shrink-0 text-zinc-500">操作系统</dt>
            <dd class="text-right text-zinc-800">{{ server.os.name }} {{ server.os.version }} ({{ server.os.arch }})</dd>
          </div>
          <div class="flex items-center justify-between gap-4">
            <dt class="shrink-0 text-zinc-500">运行时</dt>
            <dd class="text-right text-zinc-800">{{ server.jvm.vendor }}</dd>
          </div>
          <div class="flex items-center justify-between gap-4">
            <dt class="shrink-0 text-zinc-500">版本</dt>
            <dd class="text-right text-zinc-800">{{ server.jvm.version }}</dd>
          </div>
          <div class="flex items-center justify-between gap-4">
            <dt class="shrink-0 text-zinc-500">运行时长</dt>
            <dd class="text-right text-zinc-800">{{ fmtUptime(server.jvm.uptime) }}</dd>
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
import { ref, h, onMounted, onUnmounted, defineComponent } from 'vue'
import { RefreshCw } from 'lucide-vue-next'
import {
  getServerInfo,
  getServiceHealth,
  type ServerInfo,
  type ServiceHealth
} from '@/api/monitor'
import { AppButton, AppCard, AppTag } from '@/components/ui'

/** 横向使用率进度条：<70% 黑色、<90% 琥珀、其余红色 */
const UsageBar = defineComponent({
  props: { percent: { type: Number, required: true } },
  setup(props) {
    return () =>
      h('div', { class: 'flex items-center gap-3' }, [
        h('div', { class: 'h-2 flex-1 overflow-hidden rounded-full bg-zinc-100' }, [
          h('div', {
            class: [
              'h-full rounded-full transition-all duration-200 ease-out',
              props.percent < 70 ? 'bg-zinc-900' : props.percent < 90 ? 'bg-amber-500' : 'bg-red-500'
            ],
            style: { width: props.percent + '%' }
          })
        ]),
        h('span', { class: 'w-10 text-right text-sm text-zinc-800' }, props.percent + '%')
      ])
  }
})

const server = ref<ServerInfo | null>(null)
const health = ref<ServiceHealth[]>([])
const loading = ref(false)
const lastUpdated = ref('')
const autoRefresh = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

const refresh = async () => {
  loading.value = true
  try {
    const [s, h] = await Promise.all([getServerInfo(), getServiceHealth()])
    server.value = s
    health.value = h
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

const fmtUptime = (ms: number) => {
  const s = Math.floor(ms / 1000)
  const d = Math.floor(s / 86400)
  const h2 = Math.floor((s % 86400) / 3600)
  const m = Math.floor((s % 3600) / 60)
  return `${d}天 ${h2}时 ${m}分`
}
</script>
