<template>
  <div class="pb-6">
    <div class="mb-4 flex items-start justify-between gap-4">
      <h2 class="text-lg font-semibold text-zinc-800">工具中心</h2>
      <AppButton variant="primary" :icon="Plus" @click="openDialog()">新建工具</AppButton>
    </div>

    <AppCard>
      <AppTable :columns="columns" :data="tools" :loading="loading" empty-text="还没有工具，点击右上角新建">
        <template #cell-toolType="{ row }">
          <AppTag :variant="row.toolType === 'http' ? 'info' : 'success'">{{ row.toolType }}</AppTag>
        </template>
        <template #cell-enabled="{ row }">
          <AppTag :variant="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '禁用' }}</AppTag>
        </template>
        <template #cell-actions="{ row }">
          <div class="flex justify-center gap-1">
            <AppButton size="sm" variant="ghost" :icon="Pencil" @click="openDialog(row)">编辑</AppButton>
            <AppButton size="sm" variant="danger-ghost" :icon="Trash2" @click="handleDelete(row.id!)">删除</AppButton>
          </div>
        </template>
      </AppTable>
    </AppCard>

    <AppDialog v-model="dialogVisible" :title="editing ? '编辑工具' : '新建工具'" width="560px" @close="resetForm">
      <AppFormItem label="名称" required>
        <AppInput v-model="form.name" placeholder="工具唯一名称，供 Agent 引用" />
      </AppFormItem>
      <AppFormItem label="类型">
        <AppRadioGroup v-model="form.toolType" :options="[{ label: 'HTTP', value: 'http' }, { label: '内置', value: 'builtin' }]" />
      </AppFormItem>
      <AppFormItem label="描述">
        <AppInput v-model="form.description" placeholder="工具用途说明" />
      </AppFormItem>
      <AppFormItem v-if="form.toolType === 'http'" label="调用地址">
        <AppInput v-model="form.endpoint" placeholder="https://..." />
      </AppFormItem>
      <AppFormItem label="参数Schema">
        <AppTextarea v-model="form.config" :rows="4" placeholder="可选：JSON 参数定义" />
      </AppFormItem>
      <AppFormItem label="状态">
        <AppRadioGroup v-model="form.enabled" numeric :options="[{ label: '启用', value: 1 }, { label: '禁用', value: 0 }]" />
      </AppFormItem>
      <template #footer>
        <AppButton @click="dialogVisible = false">取消</AppButton>
        <AppButton variant="primary" :loading="submitting" @click="handleSubmit">
          {{ editing ? '保存修改' : '确认创建' }}
        </AppButton>
      </template>
    </AppDialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 工具中心页：工具 CRUD（http/builtin 两种类型）。
 * config 字段为 JSON：{method, params:[{name,type,description,required}], headers}，
 * http 工具在 Agent 运行时通过 httpx 按该 Schema 调用。
 */
import { ref, reactive, onMounted } from 'vue'
import { Plus, Pencil, Trash2 } from 'lucide-vue-next'
import { listTools, addTool, updateTool, deleteTool, type Tool } from '@/api/tool'
import {
  AppButton, AppCard, AppTable, AppTag, AppDialog, AppFormItem, AppInput, AppTextarea,
  AppRadioGroup, toast, confirm, type TableColumn
} from '@/components/ui'

const loading = ref(false)
const submitting = ref(false)
const tools = ref<Tool[]>([])
const dialogVisible = ref(false)
const editing = ref<Tool | null>(null)

const columns: TableColumn[] = [
  { key: 'id', label: 'ID', width: '70px', align: 'center' },
  { key: 'name', label: '名称', width: '160px' },
  { key: 'toolType', label: '类型', width: '100px', align: 'center' },
  { key: 'description', label: '描述' },
  { key: 'endpoint', label: '调用地址' },
  { key: 'enabled', label: '状态', width: '90px', align: 'center' },
  { key: 'actions', label: '操作', width: '160px', align: 'center' }
]

const form = reactive<Tool>({ name: '', description: '', toolType: 'http', endpoint: '', config: '', enabled: 1 })

onMounted(load)

async function load() {
  loading.value = true
  try { tools.value = await listTools() } catch { tools.value = [] } finally { loading.value = false }
}

function openDialog(row?: Tool) {
  editing.value = row || null
  if (row) Object.assign(form, row)
  else Object.assign(form, { name: '', description: '', toolType: 'http', endpoint: '', config: '', enabled: 1 })
  dialogVisible.value = true
}
function resetForm() {
  editing.value = null
}

async function handleSubmit() {
  if (!form.name.trim()) {
    toast.warning('请输入名称')
    return
  }
  submitting.value = true
  try {
    if (editing.value) { await updateTool({ ...form, id: editing.value.id }); toast.success('修改成功') }
    else { await addTool(form); toast.success('创建成功') }
    dialogVisible.value = false
    load()
  } catch { } finally { submitting.value = false }
}

async function handleDelete(id: number) {
  const ok = await confirm({ title: '删除确认', message: '确定删除该工具吗？', confirmText: '确定删除', danger: true })
  if (!ok) return
  try { await deleteTool(id); toast.success('删除成功'); load() } catch { }
}
</script>
