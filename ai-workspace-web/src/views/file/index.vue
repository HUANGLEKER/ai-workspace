<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>文件中心</h2>
      <el-upload
        action="/api/file/upload"
        :headers="uploadHeaders"
        :show-file-list="false"
        :on-success="onUploadSuccess"
        :on-error="onUploadError"
        multiple
      >
        <el-button type="primary" :icon="Upload">上传文件</el-button>
      </el-upload>
    </div>

    <el-card shadow="never">
      <!-- 搜索栏 -->
      <div class="toolbar">
        <el-input
          v-model="searchName"
          placeholder="搜索文件名..."
          :prefix-icon="Search"
          clearable
          style="width: 260px"
          @input="handleSearch"
        />
        <el-button :icon="Refresh" @click="loadFiles">刷新</el-button>
      </div>

      <el-table :data="files" v-loading="loading" stripe border style="margin-top:12px">
        <el-table-column label="文件名" prop="fileName" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:8px">
              <el-icon :color="fileIconColor(row.fileType)" size="18">
                <component :is="fileIcon(row.fileType)" />
              </el-icon>
              <span>{{ row.fileName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" prop="fileType" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.fileType?.toUpperCase() || '—' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" prop="fileSize" width="100" align="right">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="上传人" prop="uploadBy" width="100" align="center" />
        <el-table-column label="上传时间" prop="createTime" width="160" align="center">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link :icon="Download" @click="handleDownload(row as FileInfo)">下载</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 0"
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        class="pagination"
        @change="loadFiles"
      />
    </el-card>
  </div>
</template>

/**
 * 文件中心页
 *
 * 功能：
 * 1. 文件列表展示（分页、按文件名搜索），按 uploadBy 隔离
 * 2. 上传文件（el-upload 直传 /api/file/upload）
 * 3. 下载文件（后端返回 MinIO 预签名 URL，浏览器直接访问）
 * 4. 删除文件（软删除）
 */
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Upload, Search, Refresh, Delete, Download } from '@element-plus/icons-vue'
import type { FileInfo } from '@/types'
import { listFiles, deleteFile, getFileUrl } from '@/api/file'

const loading = ref(false)
const files = ref<FileInfo[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchName = ref('')
let searchTimer: ReturnType<typeof setTimeout>

// el-upload 不走 Axios 拦截器，需手动注入 Authorization 头
const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${localStorage.getItem('token') || ''}`
}))

onMounted(loadFiles)

async function loadFiles() {
  loading.value = true
  try {
    const res = await listFiles({ page: page.value, size: pageSize.value, fileName: searchName.value || undefined })
    files.value = res.records
    total.value = res.total
  } catch {
    files.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  // 防抖 400ms，避免每次击键都触发接口请求
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    page.value = 1
    loadFiles()
  }, 400)
}

function onUploadSuccess() {
  ElMessage.success('上传成功')
  loadFiles()
}

function onUploadError() {
  ElMessage.error('上传失败')
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除该文件吗？', '删除确认', {
    confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning'
  }).catch(() => { throw new Error('cancel') })
  try {
    await deleteFile(id)
    ElMessage.success('删除成功')
    loadFiles()
  } catch (e) {
    if ((e as Error).message !== 'cancel') ElMessage.error('删除失败')
  }
}

async function handleDownload(file: FileInfo) {
  try {
    // 后端按文件 ID 校验归属后返回 MinIO 预签名 URL，直接在新标签页打开
    const url = await getFileUrl(file.id)
    window.open(url, '_blank')
  } catch {
    ElMessage.error('下载失败')
  }
}

const fileIcon = (type: string) => {
  const t = (type || '').toLowerCase()
  if (['pdf'].includes(t)) return 'Document'
  if (['doc', 'docx'].includes(t)) return 'Memo'
  if (['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(t)) return 'Picture'
  if (['mp4', 'avi', 'mov'].includes(t)) return 'VideoPlay'
  if (['mp3', 'wav'].includes(t)) return 'Headset'
  if (['zip', 'rar', '7z'].includes(t)) return 'Files'
  return 'Document'
}

const fileIconColor = (type: string) => {
  const t = (type || '').toLowerCase()
  if (t === 'pdf') return '#F56C6C'
  if (['doc', 'docx'].includes(t)) return '#409EFF'
  if (['jpg', 'jpeg', 'png', 'gif'].includes(t)) return '#67C23A'
  return '#909399'
}

const formatSize = (bytes: number) => {
  if (!bytes) return '—'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

const formatDate = (d: string) => d ? new Date(d).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-') : '—'
</script>

<style scoped>
.page-wrapper { padding-bottom: 20px; }
.toolbar { display: flex; gap: 12px; align-items: center; }
.pagination { margin-top: 16px; justify-content: flex-end; }
</style>
