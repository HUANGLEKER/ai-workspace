<template>
  <div class="pb-6">
    <div class="mb-4 flex items-start justify-between gap-4">
      <h2 class="text-lg font-semibold text-zinc-800">文件中心</h2>
      <AppUpload action="/api/file/upload" multiple @success="onUploadSuccess" @error="onUploadError">
        <AppButton variant="primary" :icon="Upload">上传文件</AppButton>
      </AppUpload>
    </div>

    <AppCard>
      <!-- 搜索栏 -->
      <div class="mb-4 flex items-center gap-3">
        <AppSearch v-model="searchName" placeholder="搜索文件名..." class="!w-64 max-w-64" @update:model-value="handleSearch" />
        <AppButton :icon="RefreshCw" @click="loadFiles">刷新</AppButton>
      </div>

      <AppTable :columns="columns" :data="files" :loading="loading">
        <template #cell-fileName="{ row }">
          <div class="flex items-center gap-2">
            <component :is="fileIcon(row.fileType)" class="h-4.5 w-4.5 shrink-0 text-zinc-500" />
            <span class="break-words">{{ row.fileName }}</span>
          </div>
        </template>
        <template #cell-fileType="{ row }">
          <AppTag variant="info">{{ row.fileType?.toUpperCase() || '—' }}</AppTag>
        </template>
        <template #cell-fileSize="{ row }">{{ formatSize(row.fileSize) }}</template>
        <template #cell-createTime="{ row }">{{ formatDate(row.createTime) }}</template>
        <template #cell-actions="{ row }">
          <div class="flex justify-center gap-1">
            <AppButton size="sm" variant="ghost" :icon="Download" @click="handleDownload(row)">下载</AppButton>
            <AppButton size="sm" variant="danger-ghost" :icon="Trash2" @click="handleDelete(row.id)">删除</AppButton>
          </div>
        </template>
      </AppTable>

      <AppPagination
        v-if="total > 0"
        v-model:page="page"
        v-model:size="pageSize"
        :total="total"
        class="mt-4"
        @change="loadFiles"
      />
    </AppCard>
  </div>
</template>

<script setup lang="ts">
/**
 * 文件中心页：文件分页列表（按 uploadBy 隔离）、上传、
 * 下载（后端返回 MinIO 预签名 URL）、删除（软删除）。
 */
import { ref, onMounted } from 'vue'
import {
  Upload, RefreshCw, Trash2, Download, FileText, Image, Video, Headphones, Archive, File as FileIcon
} from 'lucide-vue-next'
import type { FileInfo } from '@/types'
import { listFiles, deleteFile, getFileUrl } from '@/api/file'
import {
  AppButton, AppCard, AppSearch, AppTable, AppTag, AppUpload, AppPagination,
  toast, confirm, type TableColumn
} from '@/components/ui'

const loading = ref(false)
const files = ref<FileInfo[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchName = ref('')
let searchTimer: ReturnType<typeof setTimeout>

const columns: TableColumn[] = [
  { key: 'fileName', label: '文件名' },
  { key: 'fileType', label: '类型', width: '90px', align: 'center' },
  { key: 'fileSize', label: '大小', width: '100px', align: 'right' },
  { key: 'uploadBy', label: '上传人', width: '110px', align: 'center' },
  { key: 'createTime', label: '上传时间', width: '170px', align: 'center' },
  { key: 'actions', label: '操作', width: '170px', align: 'center' }
]

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
  toast.success('上传成功')
  loadFiles()
}

function onUploadError() {
  toast.error('上传失败')
}

async function handleDelete(id: number) {
  const ok = await confirm({
    title: '删除确认', message: '确定删除该文件吗？', confirmText: '确定删除', danger: true
  })
  if (!ok) return
  try {
    await deleteFile(id)
    toast.success('删除成功')
    loadFiles()
  } catch {
    toast.error('删除失败')
  }
}

async function handleDownload(file: FileInfo) {
  try {
    // 后端按文件 ID 校验归属后返回 MinIO 预签名 URL，直接在新标签页打开
    const url = await getFileUrl(file.id)
    window.open(url, '_blank')
  } catch {
    toast.error('下载失败')
  }
}

const fileIcon = (type: string) => {
  const t = (type || '').toLowerCase()
  if (['pdf', 'doc', 'docx', 'txt', 'md'].includes(t)) return FileText
  if (['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(t)) return Image
  if (['mp4', 'avi', 'mov'].includes(t)) return Video
  if (['mp3', 'wav'].includes(t)) return Headphones
  if (['zip', 'rar', '7z'].includes(t)) return Archive
  return FileIcon
}

const formatSize = (bytes: number) => {
  if (!bytes) return '—'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

const formatDate = (d: string) => d ? new Date(d).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-') : '—'
</script>
