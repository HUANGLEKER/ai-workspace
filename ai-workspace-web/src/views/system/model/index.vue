<template>
  <div class="pb-6">
    <div class="mb-4 flex items-start justify-between gap-4">
      <h2 class="text-lg font-semibold text-zinc-800 dark:text-zinc-100">模型管理</h2>
      <AppButton variant="primary" :icon="Plus" @click="openDialog()">新增模型</AppButton>
    </div>

    <AppCard>
      <!-- 搜索栏 -->
      <div class="mb-4 flex flex-wrap items-center gap-3">
        <AppSearch v-model="searchName" placeholder="搜索模型名称..." class="!w-56 max-w-56" @enter="loadModels" />
        <AppButton variant="primary" :icon="Search" @click="loadModels">搜索</AppButton>
        <AppButton @click="resetSearch">重置</AppButton>
      </div>

      <AppTable :columns="columns" :data="models" :loading="loading">
        <template #cell-apiUrl="{ row }">{{ row.apiUrl || '默认' }}</template>
        <template #cell-enabled="{ row }">
          <AppSwitch
            :model-value="row.enabled === 1"
            @change="(v: boolean) => handleToggleStatus(row, v)"
          />
        </template>
        <template #cell-createTime="{ row }">{{ formatDate(row.createTime) }}</template>
        <template #cell-actions="{ row }">
          <div class="flex justify-center gap-1">
            <AppButton size="sm" variant="ghost" :icon="Pencil" @click="openDialog(row)">编辑</AppButton>
            <AppButton size="sm" variant="danger-ghost" :icon="Trash2" @click="handleDelete(row.id!)">删除</AppButton>
          </div>
        </template>
      </AppTable>

      <AppPagination
        v-if="total > 0"
        v-model:page="page"
        v-model:size="pageSize"
        :total="total"
        class="mt-4"
        @change="loadModels"
      />
    </AppCard>

    <!-- 新增/编辑弹窗 -->
    <AppDialog v-model="dialogVisible" :title="editing ? '编辑模型' : '新增模型'" width="480px" @close="resetForm">
      <AppFormItem label="模型名称" required>
        <AppInput v-model="form.modelName" placeholder="如 gpt-4o、deepseek-chat" />
      </AppFormItem>
      <AppFormItem label="提供商" required>
        <AppInput v-model="form.provider" placeholder="如 OpenAI、DeepSeek、Ollama" />
      </AppFormItem>
      <AppFormItem label="API 地址">
        <AppInput v-model="form.apiUrl" placeholder="留空则使用服务端默认配置" />
      </AppFormItem>
      <AppFormItem label="API Key">
        <AppInput
          v-model="form.apiKey"
          type="password"
          :placeholder="editing ? '留空则不修改' : '留空则使用服务端默认配置'"
        />
      </AppFormItem>
      <AppFormItem label="状态">
        <AppRadioGroup v-model="form.enabled" numeric :options="[{ label: '启用', value: 1 }, { label: '禁用', value: 0 }]" />
      </AppFormItem>
      <template #footer>
        <AppButton @click="dialogVisible = false">取消</AppButton>
        <AppButton variant="primary" :loading="submitting" @click="handleSubmit">
          {{ editing ? '保存修改' : '确认添加' }}
        </AppButton>
      </template>
    </AppDialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 模型管理页（仅管理员）：分页查询、新增/编辑/删除、启用/禁用。
 * apiKey 在列表中由后端脱敏不返回，编辑弹窗中留空则不修改原有值。
 */
import { ref, reactive, onMounted } from 'vue'
import { Plus, Search, Pencil, Trash2 } from 'lucide-vue-next'
import type { ChatModel } from '@/types'
import { pageModels, addModel, updateModel, deleteModel, toggleModelStatus } from '@/api/chat'
import {
  AppButton, AppCard, AppSearch, AppTable, AppSwitch, AppDialog, AppFormItem, AppInput,
  AppRadioGroup, AppPagination, toast, confirm, type TableColumn
} from '@/components/ui'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const models = ref<ChatModel[]>([])
const editing = ref<ChatModel | null>(null)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchName = ref('')

const columns: TableColumn[] = [
  { key: 'id', label: 'ID', width: '70px', align: 'center' },
  { key: 'modelName', label: '模型名称', width: '180px' },
  { key: 'provider', label: '提供商', width: '130px' },
  { key: 'apiUrl', label: 'API 地址' },
  { key: 'enabled', label: '状态', width: '90px', align: 'center' },
  { key: 'createTime', label: '创建时间', width: '170px', align: 'center' },
  { key: 'actions', label: '操作', width: '160px', align: 'center' }
]

const emptyForm = (): ChatModel => ({ modelName: '', provider: '', apiUrl: '', apiKey: '', enabled: 1 })
const form = reactive<ChatModel>(emptyForm())

onMounted(loadModels)

async function loadModels() {
  loading.value = true
  try {
    const res = await pageModels({ page: page.value, size: pageSize.value, modelName: searchName.value || undefined })
    models.value = res.records
    total.value = res.total
  } catch {
    models.value = []
  } finally {
    loading.value = false
  }
}

function resetSearch() {
  searchName.value = ''
  page.value = 1
  loadModels()
}

function openDialog(model?: ChatModel) {
  editing.value = model || null
  // 编辑时清空 apiKey 字段，留空提交则后端保持原值不变
  Object.assign(form, model ? { ...model, apiKey: '' } : emptyForm())
  dialogVisible.value = true
}

function resetForm() {
  editing.value = null
}

async function handleSubmit() {
  if (!form.modelName.trim() || !form.provider?.trim()) {
    toast.warning('请填写模型名称和提供商')
    return
  }
  submitting.value = true
  try {
    if (editing.value) {
      await updateModel(form)
      toast.success('修改成功')
    } else {
      await addModel(form)
      toast.success('添加成功')
    }
    dialogVisible.value = false
    loadModels()
  } catch {
    // 拦截器处理
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: number) {
  const ok = await confirm({
    title: '删除确认', message: '确定删除该模型吗？', confirmText: '确定删除', danger: true
  })
  if (!ok) return
  try {
    await deleteModel(id)
    toast.success('删除成功')
    loadModels()
  } catch {
    toast.error('删除失败')
  }
}

async function handleToggleStatus(row: ChatModel, enabled: boolean) {
  const newStatus = enabled ? 1 : 0
  try {
    await toggleModelStatus(row.id!, newStatus)
    row.enabled = newStatus
    toast.success(enabled ? '模型已启用' : '模型已禁用')
  } catch {
    // 拦截器处理
  }
}

const formatDate = (d?: string) => d ? new Date(d).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-') : '—'
</script>
