<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>工作流</h2>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新建工作流</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="workflows" v-loading="loading" stripe border>
        <el-table-column label="ID" prop="id" width="70" align="center" />
        <el-table-column label="名称" prop="name" width="160" />
        <el-table-column label="描述" prop="description" min-width="200" show-overflow-tooltip />
        <el-table-column label="模型" prop="model" width="140">
          <template #default="{ row }">{{ row.model || '默认' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="VideoPlay" @click="openRun(row as Workflow)">运行</el-button>
            <el-button link :icon="Edit" @click="openDialog(row as Workflow)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && workflows.length === 0" description="还没有工作流，点击右上角新建" />
    </el-card>

    <!-- 新增/编辑 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑工作流' : '新建工作流'" width="560px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="给工作流起个名字" clearable />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="一句话描述用途" clearable />
        </el-form-item>
        <el-form-item label="定义(JSON)">
          <el-input v-model="form.definition" type="textarea" :rows="4" placeholder="可选：节点/连线定义，留空使用默认单节点流程" />
        </el-form-item>
        <el-form-item label="模型">
          <el-select v-model="form.model" placeholder="留空使用默认模型" clearable style="width:100%">
            <el-option v-for="m in models" :key="m.modelName" :label="m.modelName" :value="m.modelName || ''" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.enabled">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ editing ? '保存修改' : '确认创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 运行 -->
    <el-dialog v-model="runVisible" :title="`运行 · ${runTarget?.name}`" width="600px">
      <el-input v-model="runPrompt" type="textarea" :rows="3" placeholder="输入提示词 (prompt)" />
      <div class="run-actions">
        <el-button type="primary" :loading="running" @click="doRun">运行</el-button>
      </div>
      <div v-if="runStatus" class="run-output">
        <div class="run-output-label">
          状态：<el-tag :type="runStatus === 'completed' ? 'success' : 'danger'" size="small">{{ runStatus }}</el-tag>
        </div>
        <pre>{{ runOutput }}</pre>
      </div>
      <template #footer>
        <el-button @click="runVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

/**
 * 工作流管理页
 *
 * 功能：
 * 1. 工作流 CRUD（definition 字段为 LangGraph 节点/连线 JSON）
 * 2. 运行工作流：传入 prompt，展示执行状态和 outputs JSON
 */
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Edit, Delete, VideoPlay } from '@element-plus/icons-vue'
import {
  listWorkflows, addWorkflow, updateWorkflow, deleteWorkflow, runWorkflow, type Workflow
} from '@/api/workflow'
import { listModels } from '@/api/chat'

const loading = ref(false)
const submitting = ref(false)
const workflows = ref<Workflow[]>([])
const models = ref<{ modelName?: string }[]>([])
const dialogVisible = ref(false)
const editing = ref<Workflow | null>(null)
const formRef = ref<FormInstance>()

const form = reactive<Workflow>({ name: '', description: '', definition: '', model: '', enabled: 1 })
const rules: FormRules = { name: [{ required: true, message: '请输入名称', trigger: 'blur' }] }

const runVisible = ref(false)
const runTarget = ref<Workflow | null>(null)
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

function openDialog(row?: Workflow) {
  editing.value = row || null
  if (row) Object.assign(form, row)
  else Object.assign(form, { name: '', description: '', definition: '', model: '', enabled: 1 })
  dialogVisible.value = true
}
function resetForm() {
  formRef.value?.resetFields()
  editing.value = null
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editing.value) { await updateWorkflow({ ...form, id: editing.value.id }); ElMessage.success('修改成功') }
    else { await addWorkflow(form); ElMessage.success('创建成功') }
    dialogVisible.value = false
    load()
  } catch { } finally { submitting.value = false }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除该工作流吗？', '删除确认', { type: 'warning' }).catch(() => { throw new Error('cancel') })
  try { await deleteWorkflow(id); ElMessage.success('删除成功'); load() } catch { }
}

function openRun(row: Workflow) {
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

<style scoped>
.page-wrapper { padding-bottom: 20px; }
.run-actions { margin-top: 12px; text-align: right; }
.run-output { margin-top: 16px; }
.run-output-label { font-weight: 600; margin-bottom: 6px; color: #303133; }
.run-output pre { white-space: pre-wrap; word-break: break-word; background: #f5f7fa; padding: 12px; border-radius: 6px; margin: 0; }
</style>
