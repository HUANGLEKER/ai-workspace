<template>
  <div class="pb-6">
    <div class="mb-4 flex items-start justify-between gap-4">
      <div class="flex items-center gap-2.5">
        <AppButton size="icon" :icon="ArrowLeft" @click="router.back()" />
        <h2 class="text-lg font-semibold text-zinc-800 dark:text-zinc-100">
          文档管理
          <span v-if="kbName" class="text-sm font-normal text-zinc-500 dark:text-zinc-400">— {{ kbName }}</span>
        </h2>
      </div>
      <div class="flex gap-2">
        <AppUpload
          action="/api/document/upload"
          :data="{ kbId }"
          multiple
          accept=".pdf,.doc,.docx,.txt,.md,.xls,.xlsx"
          :before-upload="beforeUpload"
          @success="onUploadSuccess"
          @error="onUploadError"
        >
          <AppButton variant="primary" :icon="Upload">上传文档</AppButton>
        </AppUpload>
        <AppTooltip content="重建 RAG 索引">
          <AppButton :icon="RefreshCw" @click="handleRebuild">重建索引</AppButton>
        </AppTooltip>
      </div>
    </div>

    <AppCard>
      <AppTable :columns="columns" :data="documents" :loading="loading">
        <template #cell-fileType="{ row }">
          <AppTag variant="info">{{ row.fileType?.toUpperCase() || '—' }}</AppTag>
        </template>
        <template #cell-fileSize="{ row }">{{ formatSize(row.fileSize) }}</template>
        <template #cell-status="{ row }">
          <AppTag :variant="statusVariant(row.status)">{{ statusText(row.status) }}</AppTag>
        </template>
        <template #cell-createTime="{ row }">{{ formatDate(row.createTime) }}</template>
        <template #cell-actions="{ row }">
          <AppButton size="sm" variant="danger-ghost" :icon="Trash2" @click="handleDelete(row.id)">删除</AppButton>
        </template>
      </AppTable>

      <AppPagination
        v-if="total > 0"
        v-model:page="page"
        v-model:size="pageSize"
        :total="total"
        class="mt-4"
        @change="loadDocuments"
      />
    </AppCard>
  </div>
</template>

<script setup lang="ts">
/**
 * 文档管理页：指定知识库下的文档分页列表，上传触发后端异步嵌入管道
 * （status 流转 PENDING→PROCESSING→DONE/FAILED），支持删除与重建索引。
 * 路由参数：query.kbId + query.kbName 从知识库管理页传入。
 */
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Upload, RefreshCw, Trash2 } from 'lucide-vue-next'
import type { KbDocument } from '@/types'
import { listDocuments, deleteDocument, rebuildRag } from '@/api/kb'
import {
  AppButton, AppCard, AppTable, AppTag, AppTooltip, AppUpload, AppPagination,
  toast, confirm, type TableColumn
} from '@/components/ui'

const route = useRoute()
const router = useRouter()

const kbId = Number(route.query.kbId)
const kbName = route.query.kbName as string

const loading = ref(false)
const documents = ref<KbDocument[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const columns: TableColumn[] = [
  { key: 'fileName', label: '文件名' },
  { key: 'fileType', label: '类型', width: '90px', align: 'center' },
  { key: 'fileSize', label: '大小', width: '100px', align: 'right' },
  { key: 'status', label: '处理状态', width: '110px', align: 'center' },
  { key: 'createTime', label: '上传时间', width: '170px', align: 'center' },
  { key: 'actions', label: '操作', width: '100px', align: 'center' }
]

onMounted(() => {
  if (!kbId) {
    toast.warning('未指定知识库，已跳转回知识库管理')
    router.replace('/knowledge/base')
    return
  }
  loadDocuments()
})

async function loadDocuments() {
  if (!kbId) return
  loading.value = true
  try {
    const res = await listDocuments({ kbId, page: page.value, size: pageSize.value })
    documents.value = res.records
    total.value = res.total
  } catch {
    documents.value = []
  } finally {
    loading.value = false
  }
}

function beforeUpload(file: File) {
  const allowed = ['application/pdf', 'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'application/vnd.ms-excel',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    'text/plain', 'text/markdown']
  const maxSize = 50 * 1024 * 1024
  // 部分浏览器对 .md / .xls 文件上报 MIME 为 text/plain 或空字符串，额外检查扩展名兜底
  const extOk = /\.(md|xls|xlsx)$/i.test(file.name)
  if (!allowed.includes(file.type) && !extOk) {
    toast.warning('仅支持 PDF、Word、Excel、TXT、Markdown 格式')
    return false
  }
  if (file.size > maxSize) {
    toast.warning('文件大小不能超过 50MB')
    return false
  }
  return true
}

function onUploadSuccess() {
  toast.success('上传成功，正在处理中...')
  loadDocuments()
}

function onUploadError() {
  toast.error('上传失败，请重试')
}

async function handleDelete(id: number) {
  const ok = await confirm({
    title: '删除确认', message: '确定删除该文档吗？', confirmText: '确定删除', danger: true
  })
  if (!ok) return
  try {
    await deleteDocument(id)
    toast.success('删除成功')
    loadDocuments()
  } catch {
    toast.error('删除失败')
  }
}

async function handleRebuild() {
  if (!kbId) {
    toast.error('知识库 ID 无效，请重新进入文档管理页')
    return
  }
  const ok = await confirm({
    title: '重建索引',
    message: '重建索引会重新处理所有文档，可能耗时较长，确认继续？',
    confirmText: '确认重建',
    danger: true
  })
  if (!ok) return
  try {
    // 后端为异步嵌入管道，接口仅提交任务即返回，实际进度需轮询文档状态
    await rebuildRag(kbId)
    toast.success('已提交重建任务，请稍后刷新查看状态')
  } catch {
    toast.error('提交失败')
  }
}

const statusText = (s: string) => ({
  PENDING: '等待处理', PROCESSING: '处理中', DONE: '已完成', FAILED: '处理失败'
}[s] || s)

const statusVariant = (s: string) =>
  (({ PENDING: 'info', PROCESSING: 'warning', DONE: 'success', FAILED: 'danger' }) as const)[
    s as 'PENDING' | 'PROCESSING' | 'DONE' | 'FAILED'
  ] || 'info'

const formatSize = (bytes: number) => {
  if (!bytes) return '—'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

const formatDate = (d: string) => d ? new Date(d).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-') : '—'
</script>
