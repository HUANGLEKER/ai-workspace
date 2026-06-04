<template>
  <div class="page-wrapper">
    <div class="page-header">
      <div style="display:flex;align-items:center;gap:10px">
        <el-button :icon="ArrowLeft" circle @click="router.back()" />
        <h2>文档管理 <span v-if="kbName" style="font-weight:400;color:#909399;font-size:14px">— {{ kbName }}</span></h2>
      </div>
      <div style="display:flex;gap:8px">
        <el-upload
          :action="`/api/document/upload`"
          :headers="uploadHeaders"
          :data="{ kbId }"
          :show-file-list="false"
          :before-upload="beforeUpload"
          :on-success="onUploadSuccess"
          :on-error="onUploadError"
          multiple
          accept=".pdf,.doc,.docx,.txt,.md"
        >
          <el-button type="primary" :icon="Upload">上传文档</el-button>
        </el-upload>
        <el-tooltip content="重建 RAG 索引">
          <el-button :icon="Refresh" @click="handleRebuild">重建索引</el-button>
        </el-tooltip>
      </div>
    </div>

    <!-- 文档表格 -->
    <el-card shadow="never">
      <el-table :data="documents" v-loading="loading" stripe border>
        <el-table-column label="文件名" prop="fileName" min-width="200" show-overflow-tooltip />
        <el-table-column label="类型" prop="fileType" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.fileType?.toUpperCase() }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="大小" prop="fileSize" width="100" align="right">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="处理状态" prop="status" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" prop="createTime" width="160" align="center">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
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
        @change="loadDocuments"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Upload, Refresh, Delete } from '@element-plus/icons-vue'
import type { KbDocument } from '@/types'
import { listDocuments, deleteDocument, rebuildRag } from '@/api/kb'

const route = useRoute()
const router = useRouter()

const kbId = Number(route.query.kbId)
const kbName = route.query.kbName as string

const loading = ref(false)
const documents = ref<KbDocument[]>([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${localStorage.getItem('token') || ''}`
}))

onMounted(loadDocuments)

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
    'text/plain', 'text/markdown']
  const maxSize = 50 * 1024 * 1024
  if (!allowed.includes(file.type) && !file.name.endsWith('.md')) {
    ElMessage.warning('仅支持 PDF、Word、TXT、Markdown 格式')
    return false
  }
  if (file.size > maxSize) {
    ElMessage.warning('文件大小不能超过 50MB')
    return false
  }
  return true
}

function onUploadSuccess() {
  ElMessage.success('上传成功，正在处理中...')
  loadDocuments()
}

function onUploadError() {
  ElMessage.error('上传失败，请重试')
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除该文档吗？', '删除确认', {
    confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning'
  }).catch(() => { throw new Error('cancel') })
  try {
    await deleteDocument(id)
    ElMessage.success('删除成功')
    loadDocuments()
  } catch (e) {
    if ((e as Error).message !== 'cancel') ElMessage.error('删除失败')
  }
}

async function handleRebuild() {
  await ElMessageBox.confirm('重建索引会重新处理所有文档，可能耗时较长，确认继续？', '重建索引', {
    confirmButtonText: '确认重建', cancelButtonText: '取消', type: 'warning'
  }).catch(() => { throw new Error('cancel') })
  try {
    await rebuildRag(kbId)
    ElMessage.success('已提交重建任务，请稍后刷新查看状态')
  } catch (e) {
    if ((e as Error).message !== 'cancel') ElMessage.error('提交失败')
  }
}

const statusText = (s: string) => ({
  PENDING: '等待处理', PROCESSING: '处理中', DONE: '已完成', FAILED: '处理失败'
}[s] || s)

const statusTagType = (s: string) => ({
  PENDING: 'info', PROCESSING: 'warning', DONE: 'success', FAILED: 'danger'
}[s] as 'info' | 'warning' | 'success' | 'danger' || 'info')

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
.pagination { margin-top: 16px; justify-content: flex-end; }
</style>
