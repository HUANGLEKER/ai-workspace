<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>Agent</h2>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新建 Agent</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="agents" v-loading="loading" stripe border>
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
            <el-button link type="primary" :icon="VideoPlay" @click="openRun(row as Agent)">运行</el-button>
            <el-button link :icon="Edit" @click="openDialog(row as Agent)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && agents.length === 0" description="还没有 Agent，点击右上角新建" />
    </el-card>

    <!-- 新增/编辑 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑 Agent' : '新建 Agent'" width="560px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="给 Agent 起个名字" clearable />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="一句话描述用途" clearable />
        </el-form-item>
        <el-form-item label="系统提示词">
          <el-input v-model="form.systemPrompt" type="textarea" :rows="4" placeholder="定义 Agent 的角色与行为" />
        </el-form-item>
        <el-form-item label="模型">
          <el-select v-model="form.model" placeholder="留空使用默认模型" clearable style="width:100%">
            <el-option v-for="m in models" :key="m.modelName" :label="m.modelName" :value="m.modelName || ''" />
          </el-select>
        </el-form-item>
        <el-form-item label="工具">
          <el-select v-model="selectedTools" multiple clearable placeholder="选择工具中心的 HTTP 工具" style="width:100%">
            <el-option v-for="t in toolOptions" :key="t.id" :label="t.name" :value="t.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="MCP 服务器">
          <el-select v-model="selectedMcp" multiple clearable placeholder="选择 SSE 类型的 MCP 服务器" style="width:100%">
            <el-option v-for="s in mcpOptions" :key="s.id" :label="s.name" :value="s.name" />
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
      <el-input v-model="runInput" type="textarea" :rows="3" placeholder="输入你的问题或指令" />
      <div class="run-actions">
        <el-button type="primary" :loading="running" @click="doRun">运行</el-button>
      </div>
      <div v-if="runOutput" class="run-output">
        <div class="run-output-label">输出</div>
        <pre>{{ runOutput }}</pre>
      </div>
      <div v-if="runSteps.length" class="run-steps">
        <div class="run-output-label">执行轨迹</div>
        <el-timeline>
          <el-timeline-item v-for="(s, i) in runSteps" :key="i" :type="s.type === 'warning' ? 'warning' : 'primary'"
            :timestamp="stepLabel(s.type)" placement="top">
            <div class="step-text">{{ stepText(s) }}</div>
          </el-timeline-item>
        </el-timeline>
      </div>
      <template #footer>
        <el-button @click="runVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Edit, Delete, VideoPlay } from '@element-plus/icons-vue'
import {
  listAgents, addAgent, updateAgent, deleteAgent, runAgent, type Agent
} from '@/api/agent'
import { listModels } from '@/api/chat'
import { listTools, type Tool } from '@/api/tool'
import { listMcpServers, type McpServer } from '@/api/mcp'

const loading = ref(false)
const submitting = ref(false)
const agents = ref<Agent[]>([])
const models = ref<{ modelName?: string }[]>([])
const toolOptions = ref<Tool[]>([])
const mcpOptions = ref<McpServer[]>([])
const dialogVisible = ref(false)
const editing = ref<Agent | null>(null)
const formRef = ref<FormInstance>()

const form = reactive<Agent>({ name: '', description: '', systemPrompt: '', model: '', enabled: 1 })
// tools / mcpServers are stored as JSON-array strings on the backend; edited as arrays here
const selectedTools = ref<string[]>([])
const selectedMcp = ref<string[]>([])
const rules: FormRules = { name: [{ required: true, message: '请输入名称', trigger: 'blur' }] }

const runVisible = ref(false)
const runTarget = ref<Agent | null>(null)
const runInput = ref('')
const runOutput = ref('')
const runSteps = ref<Record<string, unknown>[]>([])
const running = ref(false)

onMounted(() => { load(); loadModels(); loadTools(); loadMcp() })

async function load() {
  loading.value = true
  try { agents.value = await listAgents() } catch { agents.value = [] } finally { loading.value = false }
}
async function loadModels() {
  try { models.value = await listModels() } catch { models.value = [] }
}
async function loadTools() {
  try { toolOptions.value = await listTools() } catch { toolOptions.value = [] }
}
async function loadMcp() {
  try { mcpOptions.value = await listMcpServers() } catch { mcpOptions.value = [] }
}

function parseNames(json?: string): string[] {
  if (!json) return []
  try { const v = JSON.parse(json); return Array.isArray(v) ? v : [] } catch { return [] }
}

function openDialog(row?: Agent) {
  editing.value = row || null
  if (row) Object.assign(form, row)
  else Object.assign(form, { name: '', description: '', systemPrompt: '', model: '', enabled: 1 })
  selectedTools.value = parseNames(row?.tools)
  selectedMcp.value = parseNames(row?.mcpServers)
  dialogVisible.value = true
}
function resetForm() {
  formRef.value?.resetFields()
  editing.value = null
  selectedTools.value = []
  selectedMcp.value = []
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  const payload: Agent = {
    ...form,
    tools: JSON.stringify(selectedTools.value),
    mcpServers: JSON.stringify(selectedMcp.value)
  }
  try {
    if (editing.value) { await updateAgent({ ...payload, id: editing.value.id }); ElMessage.success('修改成功') }
    else { await addAgent(payload); ElMessage.success('创建成功') }
    dialogVisible.value = false
    load()
  } catch { } finally { submitting.value = false }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除该 Agent 吗？', '删除确认', { type: 'warning' }).catch(() => { throw new Error('cancel') })
  try { await deleteAgent(id); ElMessage.success('删除成功'); load() } catch { }
}

function openRun(row: Agent) {
  runTarget.value = row
  runInput.value = ''
  runOutput.value = ''
  runSteps.value = []
  runVisible.value = true
}
async function doRun() {
  if (!runInput.value.trim() || !runTarget.value?.id) return
  running.value = true
  runOutput.value = ''
  runSteps.value = []
  try {
    const res = await runAgent(runTarget.value.id, runInput.value)
    runOutput.value = res.output || '(无输出)'
    runSteps.value = res.steps || []
  } catch { } finally { running.value = false }
}

function stepLabel(t: unknown): string {
  const map: Record<string, string> = {
    llm: '模型', tool_call: '调用工具', tool_result: '工具结果', mcp: 'MCP', warning: '警告'
  }
  return map[String(t)] || String(t)
}
function stepText(s: Record<string, unknown>): string {
  if (s.type === 'tool_call') return `${s.tool}(${JSON.stringify(s.args ?? {})})`
  return String(s.content ?? '')
}
</script>

<style scoped>
.page-wrapper { padding-bottom: 20px; }
.run-actions { margin-top: 12px; text-align: right; }
.run-output { margin-top: 16px; }
.run-output-label { font-weight: 600; margin-bottom: 6px; color: #303133; }
.run-output pre { white-space: pre-wrap; word-break: break-word; background: #f5f7fa; padding: 12px; border-radius: 6px; margin: 0; }
.run-steps { margin-top: 16px; }
.step-text { white-space: pre-wrap; word-break: break-word; font-size: 13px; color: #606266; }
</style>
