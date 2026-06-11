<template>
  <div class="pb-6">
    <div class="mb-4 flex items-start justify-between gap-4">
      <h2 class="text-lg font-semibold text-zinc-800 dark:text-zinc-100">工作流</h2>
      <AppButton variant="primary" :icon="Plus" @click="openDialog()">新建工作流</AppButton>
    </div>

    <!-- 工作流卡片网格 -->
    <div class="relative grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
      <div
        v-for="w in workflows"
        :key="w.id"
        class="flex flex-col rounded-2xl border border-zinc-200/80 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-5 shadow-[0_10px_30px_-10px_rgba(0,0,0,0.04)] transition-all duration-200 ease-out hover:shadow-md"
      >
        <div class="mb-2 flex items-center gap-3">
          <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-zinc-100 dark:bg-zinc-800/50">
            <Workflow class="h-5 w-5 text-zinc-800 dark:text-zinc-100" />
          </div>
          <div class="min-w-0 flex-1">
            <div class="truncate text-sm font-semibold text-zinc-800 dark:text-zinc-100">{{ w.name }}</div>
            <AppTag :variant="w.enabled === 1 ? 'success' : 'info'">{{ w.enabled === 1 ? '启用' : '禁用' }}</AppTag>
          </div>
        </div>
        <p class="line-clamp-2 flex-1 text-sm text-zinc-500 dark:text-zinc-400">{{ w.description || '暂无描述' }}</p>
        <div class="mt-2 text-xs text-zinc-400 dark:text-zinc-500">模型：{{ w.model || '默认' }}</div>
        <div class="mt-4 flex gap-1 border-t border-zinc-200/80 dark:border-zinc-800 pt-3">
          <AppButton size="sm" variant="ghost" :icon="Play" @click="openRun(w)">运行</AppButton>
          <AppButton size="sm" variant="ghost" :icon="Pencil" @click="openDialog(w)">编辑</AppButton>
          <AppButton size="sm" variant="danger-ghost" :icon="Trash2" @click="handleDelete(w.id!)">删除</AppButton>
        </div>
      </div>

      <AppEmpty v-if="!loading && workflows.length === 0" class="col-span-full py-16" description="还没有工作流，点击右上角新建" :icon="Workflow" />
      <AppLoading v-if="loading" overlay />
    </div>

    <!-- 新增/编辑 -->
    <AppDialog v-model="dialogVisible" :title="editing ? '编辑工作流' : '新建工作流'" width="560px" @close="resetForm">
      <AppFormItem label="名称" required>
        <AppInput v-model="form.name" placeholder="给工作流起个名字" />
      </AppFormItem>
      <AppFormItem label="描述">
        <AppInput v-model="form.description" placeholder="一句话描述用途" />
      </AppFormItem>
      <AppFormItem label="定义(JSON)">
        <AppTextarea v-model="form.definition" :rows="4" placeholder="可选：节点/连线定义，留空使用默认单节点流程" />
      </AppFormItem>
      <AppFormItem label="模型">
        <AppSelect v-model="form.model" :options="modelOptions" placeholder="留空使用默认模型" />
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

    <!-- 运行 -->
    <AppDialog v-model="runVisible" :title="`运行 · ${runTarget?.name}`" width="600px">
      <AppTextarea v-model="runPrompt" :rows="3" placeholder="输入提示词 (prompt)" />
      <div class="mt-3 text-right">
        <AppButton variant="primary" :loading="running" @click="doRun">运行</AppButton>
      </div>
      <div v-if="runStatus" class="mt-4">
        <div class="mb-1.5 flex items-center gap-2 text-sm font-semibold text-zinc-800 dark:text-zinc-100">
          状态：
          <AppTag :variant="runStatus === 'completed' ? 'success' : 'danger'">{{ runStatus }}</AppTag>
        </div>
        <pre class="whitespace-pre-wrap break-words rounded-xl bg-zinc-50 dark:bg-zinc-950 p-3 text-sm text-zinc-800 dark:text-zinc-100">{{ runOutput }}</pre>
      </div>
      <template #footer>
        <AppButton @click="runVisible = false">关闭</AppButton>
      </template>
    </AppDialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 工作流管理页：Card Grid + CRUD（definition 字段为 LangGraph 节点/连线 JSON），
 * 运行工作流展示执行状态和 outputs JSON。
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus, Workflow, Play, Pencil, Trash2 } from 'lucide-vue-next'
import {
  listWorkflows, addWorkflow, updateWorkflow, deleteWorkflow, runWorkflow, type Workflow as WorkflowType
} from '@/api/workflow'
import { listModels } from '@/api/chat'
import {
  AppButton, AppDialog, AppFormItem, AppInput, AppTextarea, AppSelect, AppRadioGroup,
  AppTag, AppEmpty, AppLoading, toast, confirm
} from '@/components/ui'

const loading = ref(false)
const submitting = ref(false)
const workflows = ref<WorkflowType[]>([])
const models = ref<{ modelName?: string }[]>([])
const dialogVisible = ref(false)
const editing = ref<WorkflowType | null>(null)

const form = reactive<WorkflowType>({ name: '', description: '', definition: '', model: '', enabled: 1 })

const modelOptions = computed(() =>
  models.value.filter((m) => m.modelName).map((m) => ({ label: m.modelName!, value: m.modelName! }))
)

const runVisible = ref(false)
const runTarget = ref<WorkflowType | null>(null)
const runPrompt = ref('')
const runOutput = ref('')
const runStatus = ref('')
const running = ref(false)

onMounted(() => { load(); loadModels() })

async function load() {
  loading.value = true
  try { workflows.value = await listWorkflows() } catch { workflows.value = [] } finally { loading.value = false }
}
async function loadModels() {
  try { models.value = await listModels() } catch { models.value = [] }
}

function openDialog(row?: WorkflowType) {
  editing.value = row || null
  if (row) Object.assign(form, row)
  else Object.assign(form, { name: '', description: '', definition: '', model: '', enabled: 1 })
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
    if (editing.value) { await updateWorkflow({ ...form, id: editing.value.id }); toast.success('修改成功') }
    else { await addWorkflow(form); toast.success('创建成功') }
    dialogVisible.value = false
    load()
  } catch { } finally { submitting.value = false }
}

async function handleDelete(id: number) {
  const ok = await confirm({ title: '删除确认', message: '确定删除该工作流吗？', confirmText: '确定删除', danger: true })
  if (!ok) return
  try { await deleteWorkflow(id); toast.success('删除成功'); load() } catch { }
}

function openRun(row: WorkflowType) {
  runTarget.value = row
  runPrompt.value = ''
  runOutput.value = ''
  runStatus.value = ''
  runVisible.value = true
}
async function doRun() {
  if (!runTarget.value?.id) return
  running.value = true
  runOutput.value = ''
  runStatus.value = ''
  try {
    const res = await runWorkflow(runTarget.value.id, runPrompt.value)
    runStatus.value = res.status
    runOutput.value = JSON.stringify(res.outputs, null, 2)
  } catch { } finally { running.value = false }
}
</script>
