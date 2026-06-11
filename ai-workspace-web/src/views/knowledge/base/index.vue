<template>
  <div class="pb-6">
    <div class="mb-4 flex items-start justify-between gap-4">
      <h2 class="text-lg font-semibold text-zinc-800">知识库管理</h2>
      <AppButton variant="primary" :icon="Plus" @click="openCreateDialog">新建知识库</AppButton>
    </div>

    <!-- 知识库卡片网格 -->
    <div class="relative grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
      <div
        v-for="kb in knowledgeBases"
        :key="kb.id"
        class="flex flex-col rounded-2xl border border-zinc-200/80 bg-white p-5 shadow-[0_10px_30px_-10px_rgba(0,0,0,0.04)] transition-all duration-200 ease-out hover:-translate-y-0.5 hover:shadow-md"
      >
        <div class="mb-3 flex items-start gap-3.5">
          <div class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-zinc-100/50">
            <BookOpen class="h-5 w-5 text-zinc-800" />
          </div>
          <div class="min-w-0">
            <div class="truncate text-sm font-semibold text-zinc-800">{{ kb.kbName }}</div>
            <p class="mt-1 line-clamp-2 text-sm text-zinc-500">{{ kb.description || '暂无描述' }}</p>
          </div>
        </div>
        <div class="flex gap-3 text-xs text-zinc-400">
          <span>创建人：{{ kb.createBy }}</span>
          <span>{{ formatDate(kb.createTime) }}</span>
        </div>
        <div class="mt-4 flex gap-2 border-t border-zinc-200/80 pt-3">
          <AppButton size="sm" :icon="FileText" @click="goDocuments(kb)">文档管理</AppButton>
          <AppButton size="sm" :icon="Pencil" @click="openEditDialog(kb)">编辑</AppButton>
          <AppButton size="sm" variant="danger-ghost" :icon="Trash2" @click="handleDelete(kb.id)">删除</AppButton>
        </div>
      </div>

      <AppEmpty
        v-if="!loading && knowledgeBases.length === 0"
        class="col-span-full py-20"
        description="暂无知识库，点击右上角新建"
        :icon="BookOpen"
      />
      <AppLoading v-if="loading" overlay />
    </div>

    <!-- 新建/编辑弹窗 -->
    <AppDialog v-model="dialogVisible" :title="editingKb ? '编辑知识库' : '新建知识库'" width="480px" @close="resetForm">
      <AppFormItem label="名称" required>
        <AppInput v-model="form.kbName" placeholder="请输入知识库名称" />
      </AppFormItem>
      <AppFormItem label="描述">
        <AppTextarea v-model="form.description" :rows="3" placeholder="请输入知识库描述（可选）" />
      </AppFormItem>
      <template #footer>
        <AppButton @click="dialogVisible = false">取消</AppButton>
        <AppButton variant="primary" :loading="submitting" @click="handleSubmit">
          {{ editingKb ? '保存修改' : '确认创建' }}
        </AppButton>
      </template>
    </AppDialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 知识库管理页：Card Grid 展示当前用户所有知识库，支持新建/编辑/删除，
 * 跳转文档管理页（携带 kbId、kbName 查询参数）。
 */
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, BookOpen, FileText, Pencil, Trash2 } from 'lucide-vue-next'
import type { KnowledgeBase } from '@/types'
import { listKnowledgeBases, createKnowledgeBase, updateKnowledgeBase, deleteKnowledgeBase } from '@/api/kb'
import {
  AppButton, AppDialog, AppInput, AppTextarea, AppFormItem, AppEmpty, AppLoading, toast, confirm
} from '@/components/ui'

const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const editingKb = ref<KnowledgeBase | null>(null)
const knowledgeBases = ref<KnowledgeBase[]>([])

const form = reactive({ kbName: '', description: '' })

const formatDate = (d: string) => d ? new Date(d).toLocaleDateString('zh-CN') : ''

onMounted(loadList)

async function loadList() {
  loading.value = true
  try {
    knowledgeBases.value = await listKnowledgeBases()
  } catch {
    knowledgeBases.value = []
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  editingKb.value = null
  dialogVisible.value = true
}

function openEditDialog(kb: KnowledgeBase) {
  editingKb.value = kb
  form.kbName = kb.kbName
  form.description = kb.description || ''
  dialogVisible.value = true
}

function resetForm() {
  form.kbName = ''
  form.description = ''
  editingKb.value = null
}

async function handleSubmit() {
  if (!form.kbName.trim()) {
    toast.warning('请输入知识库名称')
    return
  }
  submitting.value = true
  try {
    if (editingKb.value) {
      await updateKnowledgeBase({ ...editingKb.value, ...form })
      toast.success('修改成功')
    } else {
      await createKnowledgeBase(form)
      toast.success('创建成功')
    }
    dialogVisible.value = false
    loadList()
  } catch {
    // 由拦截器处理
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: number) {
  const ok = await confirm({
    title: '删除确认',
    message: '确定删除该知识库吗？删除后相关文档也将一并删除。',
    confirmText: '确定删除',
    danger: true
  })
  if (!ok) return
  try {
    await deleteKnowledgeBase(id)
    toast.success('删除成功')
    loadList()
  } catch {
    toast.error('删除失败')
  }
}

function goDocuments(kb: KnowledgeBase) {
  router.push({ path: '/knowledge/document', query: { kbId: kb.id, kbName: kb.kbName } })
}
</script>
