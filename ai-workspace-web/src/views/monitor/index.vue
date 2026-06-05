<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>系统监控</h2>
      <div class="header-actions">
        <span v-if="lastUpdated" class="update-text">更新于 {{ lastUpdated }}</span>
        <el-button :loading="loading" @click="refresh">刷新</el-button>
        <el-button :type="autoRefresh ? 'primary' : 'default'" @click="toggleAuto">
          {{ autoRefresh ? '关闭自动刷新' : '自动刷新 (5s)' }}
        </el-button>
      </div>
    </div>

    <!-- 依赖服务健康 -->
    <el-card shadow="never" class="section">
      <template #header><span>依赖服务</span></template>
      <el-row :gutter="16">
        <el-col :xs="24" :sm="8" v-for="svc in health" :key="svc.name">
          <div class="svc-item">
            <span class="svc-dot" :class="svc.status === 'UP' ? 'up' : 'down'" />
            <div class="svc-info">
              <div class="svc-name">{{ svc.name }}</div>
              <div class="svc-meta">
                <el-tag :type="svc.status === 'UP' ? 'success' : 'danger'" size="small">
                  {{ svc.status }}
                </el-tag>
                <span v-if="svc.status === 'UP'" class="svc-latency">{{ svc.latencyMs }} ms</span>
                <span v-else class="svc-error">{{ svc.error }}</span>
              </div>
              <div class="svc-target">{{ svc.target }}</div>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 资源使用率 -->
    <el-row :gutter="16" class="section" v-if="server">
      <el-col :xs="24" :sm="8">
        <el-card shadow="never">
          <template #header><span>CPU</span></template>
          <el-progress type="dashboard" :percentage="pct(server.cpu.sysUsedPercent)" :color="gaugeColor" />
          <div class="gauge-foot">
            <div>系统使用率 {{ fmtPct(server.cpu.sysUsedPercent) }}</div>
            <div>进程使用率 {{ fmtPct(server.cpu.procUsedPercent) }}</div>
            <div>逻辑核心 {{ server.cpu.cores }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="never">
          <template #header><span>物理内存</span></template>
          <el-progress type="dashboard" :percentage="pct(server.memory.usedPercent)" :color="gaugeColor" />
          <div class="gauge-foot">
            <div>{{ fmtBytes(server.memory.used) }} / {{ fmtBytes(server.memory.total) }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8">
        <el-card shadow="never">
          <template #header><span>JVM 堆内存</span></template>
          <el-progress type="dashboard" :percentage="pct(server.jvm.usedPercent)" :color="gaugeColor" />
          <div class="gauge-foot">
            <div>{{ fmtBytes(server.jvm.used) }} / {{ fmtBytes(server.jvm.max) }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 磁盘 + 运行环境 -->
    <el-row :gutter="16" class="section" v-if="server">
      <el-col :xs="24" :sm="14">
        <el-card shadow="never">
          <template #header><span>磁盘</span></template>
          <el-table :data="server.disks" size="small">
            <el-table-column prop="path" label="挂载点" />
            <el-table-column label="已用 / 总量">
              <template #default="{ row }">
                {{ fmtBytes(row.used) }} / {{ fmtBytes(row.total) }}
              </template>
            </el-table-column>
            <el-table-column label="使用率" width="200">
              <template #default="{ row }">
                <el-progress :percentage="pct(row.usedPercent)" :color="gaugeColor" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="10">
        <el-card shadow="never">
          <template #header><span>运行环境</span></template>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="操作系统">
              {{ server.os.name }} {{ server.os.version }} ({{ server.os.arch }})
            </el-descriptions-item>
            <el-descriptions-item label="JVM">{{ server.jvm.vendor }}</el-descriptions-item>
            <el-descriptions-item label="Java 版本">{{ server.jvm.version }}</el-descriptions-item>
            <el-descriptions-item label="运行时长">{{ fmtUptime(server.jvm.uptime) }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import {
  getServerInfo,
  getServiceHealth,
  type ServerInfo,
  type ServiceHealth
} from '@/api/monitor'

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

// a load of -1 means the metric was unavailable on this JVM/OS
const pct = (v: number) => (v < 0 ? 0 : Math.round(v))
const fmtPct = (v: number) => (v < 0 ? 'N/A' : v.toFixed(1) + '%')
const gaugeColor = (p: number) => (p < 70 ? '#67C23A' : p < 90 ? '#E6A23C' : '#F56C6C')

const fmtBytes = (bytes: number) => {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return (bytes / Math.pow(1024, i)).toFixed(1) + ' ' + units[i]
}

const fmtUptime = (ms: number) => {
  const s = Math.floor(ms / 1000)
  const d = Math.floor(s / 86400)
  const h = Math.floor((s % 86400) / 3600)
  const m = Math.floor((s % 3600) / 60)
  return `${d}天 ${h}时 ${m}分`
}
</script>

<style scoped>
.page-wrapper { padding-bottom: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; }
.header-actions { display: flex; align-items: center; gap: 12px; }
.update-text { color: #909399; font-size: 13px; }
.section { margin-top: 16px; }
.svc-item { display: flex; align-items: flex-start; gap: 12px; padding: 6px 0; }
.svc-dot { width: 12px; height: 12px; border-radius: 50%; margin-top: 4px; flex-shrink: 0; }
.svc-dot.up { background: #67C23A; box-shadow: 0 0 6px #67C23A; }
.svc-dot.down { background: #F56C6C; box-shadow: 0 0 6px #F56C6C; }
.svc-name { font-weight: 600; color: #303133; }
.svc-meta { display: flex; align-items: center; gap: 8px; margin: 4px 0; }
.svc-latency { color: #67C23A; font-size: 13px; }
.svc-error { color: #F56C6C; font-size: 12px; }
.svc-target { color: #909399; font-size: 12px; word-break: break-all; }
.gauge-foot { text-align: center; margin-top: 8px; color: #606266; font-size: 13px; line-height: 1.6; }
</style>
