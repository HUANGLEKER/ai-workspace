<template>
  <PageShell gap="lg" scroll>
    <PageHeader
      title="知识库"
      description="管理个人知识库，进入文档管理后可上传资料并构建 RAG 索引。"
      :icon="BookOpen"
    >
      <template #actions>
        <AppButton variant="primary" :icon="Plus" @click="openCreateDialog">新建知识库</AppButton>
      </template>
    </PageHeader>

    <PageToolbar>
      <AppInput v-model="keyword" placeholder="搜索知识库名称或描述" class="w-full sm:w-80" />
      <span class="text-sm text-zinc-400 dark:text-zinc-500">共 {{ knowledgeBases.length }} 个知识库</span>
      <template #actions>
        <AppButton :icon="RefreshCcw" :loading="loading" @click="loadList">刷新</AppButton>
      </template>
    </PageToolbar>

    <div class="relative min-h-[320px]">
      <div v-if="filteredKnowledgeBases.length" class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <ResourceCard
          v-for="kb in filteredKnowledgeBases"
          :key="kb.id"
          :title="kb.kbName"
          :description="kb.description || '暂无描述'"
          :icon="BookOpen"
        >
          <template #meta>
            <span>创建人：{{ creatorName }}</span>
            <span>{{ formatDate(kb.createTime) }}</span>
          </template>
          <template #actions>
            <AppButton size="sm" :icon="FileText" @click="goDocuments(kb)">文档管理</AppButton>
            <AppButton size="sm" :icon="Pencil" @click="openEditDialog(kb)">编辑</AppButton>
            <AppButton size="sm" variant="danger-ghost" :icon="Trash2" @click="handleDelete(kb.id)">删除</AppButton>
          </template>
        </ResourceCard>
      </div>

      <AppEmpty
        v-if="!loading && !filteredKnowledgeBases.length"
        class="py-20"
        :description="keyword ? '没有匹配的知识库' : '暂无知识库，点击右上角新建'"
        :icon="BookOpen"
      />
      <AppLoading v-if="loading" overlay />
    </div>

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
  </PageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { BookOpen, FileText, Pencil, Plus, RefreshCcw, Trash2 } from 'lucide-vue-next'
import type { KnowledgeBase } from '@/types'
import { createKnowledgeBase, deleteKnowledgeBase, listKnowledgeBases, updateKnowledgeBase } from '@/api/kb'
import { useAuthStore } from '@/stores/auth'
import {
  AppButton,
  AppDialog,
  AppEmpty,
  AppFormItem,
  AppInput,
  AppLoading,
  AppTextarea,
  PageHeader,
  PageShell,
  PageToolbar,
  ResourceCard,
  confirm,
  toast
} from '@/components/ui'

const router = useRouter()
const authStore = useAuthStore()

const creatorName = computed(() => authStore.userInfo?.nickname || authStore.userInfo?.username || '我')
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const editingKb = ref<KnowledgeBase | null>(null)
const knowledgeBases = ref<KnowledgeBase[]>([])
const keyword = ref('')

const form = reactive({ kbName: '', description: '' })

const filteredKnowledgeBases = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return knowledgeBases.value
  return knowledgeBases.value.filter((kb) =>
    [kb.kbName, kb.description || ''].some((text) => text.toLowerCase().includes(q))
  )
})

const formatDate = (d: string) => (d ? new Date(d).toLocaleDateString('zh-CN') : '')

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
    await loadList()
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
    await loadList()
  } catch {
    toast.error('删除失败')
  }
}

function goDocuments(kb: KnowledgeBase) {
  router.push({ path: '/knowledge/document', query: { kbId: kb.id, kbName: kb.kbName } })
}
</script>
