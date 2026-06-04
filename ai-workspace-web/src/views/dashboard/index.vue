<template>
  <div class="dashboard">
    <div class="page-header">
      <h2>仪表盘</h2>
      <span class="date-text">{{ todayText }}</span>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :sm="6" v-for="stat in stats" :key="stat.label">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-inner">
            <div class="stat-icon" :style="{ background: stat.color }">
              <el-icon size="22" color="#fff"><component :is="stat.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stat.value }}</div>
              <div class="stat-label">{{ stat.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 欢迎区域 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <span>快速入口</span>
          </template>
          <div class="quick-actions">
            <div
              v-for="action in quickActions"
              :key="action.label"
              class="quick-item"
              @click="router.push(action.path)"
            >
              <div class="quick-icon" :style="{ background: action.color }">
                <el-icon size="20" color="#fff"><component :is="action.icon" /></el-icon>
              </div>
              <span>{{ action.label }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <span>系统信息</span>
          </template>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="版本">v1.0.0</el-descriptions-item>
            <el-descriptions-item label="前端框架">Vue 3 + Element Plus</el-descriptions-item>
            <el-descriptions-item label="后端框架">Spring Boot 3.5</el-descriptions-item>
            <el-descriptions-item label="AI 框架">LangChain + LangGraph</el-descriptions-item>
            <el-descriptions-item label="向量数据库">ChromaDB</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboardStats } from '@/api/dashboard'

const router = useRouter()

const todayText = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric', month: 'long', day: 'numeric', weekday: 'long'
})

const stats = ref([
  { label: '今日对话', value: 0, icon: 'ChatDotRound', color: '#409EFF' },
  { label: '知识库数量', value: 0, icon: 'Reading', color: '#67C23A' },
  { label: '文档总数', value: 0, icon: 'Document', color: '#E6A23C' },
  { label: '文件总数', value: 0, icon: 'Folder', color: '#F56C6C' }
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
  { label: '新建对话', icon: 'ChatDotRound', path: '/chat', color: '#409EFF' },
  { label: '知识库管理', icon: 'Reading', path: '/knowledge/base', color: '#67C23A' },
  { label: '上传文件', icon: 'Folder', path: '/file', color: '#E6A23C' },
  { label: '提示词中心', icon: 'MagicStick', path: '/prompt', color: '#9C27B0' },
  { label: '工作流', icon: 'Connection', path: '/workflow', color: '#00BCD4' },
  { label: 'Agent', icon: 'Cpu', path: '/agent', color: '#FF5722' }
]
</script>

<style scoped>
.dashboard {
  padding-bottom: 20px;
}

.date-text {
  color: #909399;
  font-size: 13px;
}

.stats-row {
  margin-bottom: 4px;
}

.stat-card {
  margin-bottom: 0;
}

.stat-inner {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 4px 0;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 2px;
}

.quick-actions {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
  font-size: 13px;
  color: #606266;
}

.quick-item:hover {
  background: #f5f7fa;
  color: #409EFF;
}

.quick-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
