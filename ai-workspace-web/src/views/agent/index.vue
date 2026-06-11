<template>
  <div class="pb-6">
    <div class="mb-4 flex items-start justify-between gap-4">
      <h2 class="text-lg font-semibold text-zinc-800">Agent</h2>
      <AppButton variant="primary" :icon="Plus" @click="openDialog()">新建 Agent</AppButton>
    </div>

    <!-- Agent 卡片网格 -->
    <div class="relative grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <div
        v-for="a in agents"
        :key="a.id"
        class="flex flex-col rounded-2xl border border-zinc-200/80 bg-white p-5 shadow-[0_10px_30px_-10px_rgba(0,0,0,0.04)] transition-all duration-200 ease-out hover:shadow-md"
      >
        <div class="mb-2 flex items-center gap-3">
          <AppAvatar :icon="Bot" variant="dark" size="lg" />
          <div class="min-w-0 flex-1">
            <div class="truncate text-sm font-semibold text-zinc-800">{{ a.name }}</div>
            <AppTag :variant="a.enabled === 1 ? 'success' : 'info'">{{ a.enabled === 1 ? '启用' : '禁用' }}</AppTag>
          </div>
        </div>
        <p class="line-clamp-2 flex-1 text-sm text-zinc-500">{{ a.description || '暂无描述' }}</p>
        <div class="mt-2 flex gap-3 text-xs text-zinc-400">
          <span>模型：{{ a.model || '默认' }}</span>
          <span>工具 {{ parseNames(a.tools).length }}</span>
          <span>MCP {{ parseNames(a.mcpServers).length }}</span>
        </div>
        <div class="mt-4 flex gap-1 border-t border-zinc-200/80 pt-3">
          <AppButton size="sm" variant="ghost" :icon="Play" @click="openRun(a)">运行</AppButton>
          <AppButton size="sm" variant="ghost" :icon="Pencil" @click="openDialog(a)">编辑</AppButton>
          <AppButton size="sm" variant="danger-ghost" :icon="Trash2" @click="handleDelete(a.id!)">删除</AppButton>
        </div>
      </div>

      <AppEmpty v-if="!loading && agents.length === 0" class="col-span-full py-16" description="还没有 Agent，点击右上角新建" :icon="Bot" />
      <AppLoading v-if="loading" overlay />
    </div>

    <!-- 新增/编辑 -->
    <AppDialog v-model="dialogVisible" :title="editing ? '编辑 Agent' : '新建 Agent'" width="560px" @close="resetForm">
      <AppFormItem label="名称" required>
        <AppInput v-model="form.name" placeholder="给 Agent 起个名字" />
      </AppFormItem>
      <AppFormItem label="描述">
        <AppInput v-model="form.description" placeholder="一句话描述用途" />
      </AppFormItem>
      <AppFormItem label="系统提示词">
        <AppTextarea v-model="form.systemPrompt" :rows="4" placeholder="定义 Agent 的角色与行为" />
      </AppFormItem>
      <AppFormItem label="模型">
        <AppSelect v-model="form.model" :options="modelOptions" placeholder="留空使用默认模型" />
      </AppFormItem>
      <AppFormItem label="工具">
        <AppMultiSelect v-model="selectedTools" :options="toolOptions.map(t => t.name)" placeholder="选择工具中心的 HTTP 工具" />
      </AppFormItem>
      <AppFormItem label="MCP 服务器">
        <AppMultiSelect v-model="selectedMcp" :options="mcpOptions.map(s => s.name)" placeholder="选择 SSE 类型的 MCP 服务器" />
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
      <AppTextarea v-model="runInput" :rows="3" placeholder="输入你的问题或指令" />
      <div class="mt-3 text-right">
        <AppButton variant="primary" :loading="running" @click="doRun">运行</AppButton>
      </div>
      <div v-if="runOutput" class="mt-4">
        <div class="mb-1.5 text-sm font-semibold text-zinc-800">输出</div>
        <pre class="whitespace-pre-wrap break-words rounded-xl bg-zinc-50 p-3 text-sm text-zinc-800">{{ runOutput }}</pre>
      </div>
      <div v-if="runSteps.length" class="mt-4">
        <div class="mb-2 text-sm font-semibold text-zinc-800">执行轨迹</div>
        <ol class="relative flex flex-col gap-3 border-l border-zinc-200/80 pl-4">
          <li v-for="(s, i) in runSteps" :key="i" class="relative">
            <span
              class="absolute -left-[21.5px] top-1.5 h-2.5 w-2.5 rounded-full"
              :class="s.type === 'warning' ? 'bg-amber-400' : 'bg-zinc-900'"
            />
            <div class="text-xs text-zinc-400">{{ stepLabel(s.type) }}</div>
            <div class="whitespace-pre-wrap break-words text-sm text-zinc-500">{{ stepText(s) }}</div>
          </li>
        </ol>
      </div>
      <template #footer>
        <AppButton @click="runVisible = false">关闭</AppButton>
      </template>
    </AppDialog>
  </div>
</template>

<script setup lang="ts">
/**
 * Agent 管理页：Card Grid + CRUD（含工具/MCP 服务器多选关联），
 * 运行 Agent 展示 output 及 steps 执行轨迹。
 *
 * tools/mcpServers 在 DB 中以 JSON 字符串存储，编辑时解析为 string[] 绑定多选，
 * 提交前重新序列化，运行时由后端 AgentService 解析为完整工具规格传给 FastAPI。
 */
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus, Bot, Play, Pencil, Trash2 } from 'lucide-vue-next'
import {
  listAgents, addAgent, updateAgent, deleteAgent, runAgent, type Agent
} from '@/api/agent'
import { listModels } from '@/api/chat'
import { listTools, type Tool } from '@/api/tool'
import { listMcpServers, type McpServer } from '@/api/mcp'
import {
  AppButton, AppDialog, AppFormItem, AppInput, AppTextarea, AppSelect, AppMultiSelect,
  AppRadioGroup, AppTag, AppAvatar, AppEmpty, AppLoading, toast, confirm
} from '@/components/ui'

const loading = ref(false)
const submitting = ref(false)
const agents = ref<Agent[]>([])
const models = ref<{ modelName?: string }[]>([])
const toolOptions = ref<Tool[]>([])
const mcpOptions = ref<McpServer[]>([])
const dialogVisible = ref(false)
const editing = ref<Agent | null>(null)

const form = reactive<Agent>({ name: '', description: '', systemPrompt: '', model: '', enabled: 1 })
const selectedTools = ref<string[]>([])
const selectedMcp = ref<string[]>([])

const modelOptions = computed(() =>
  models.value.filter((m) => m.modelName).map((m) => ({ label: m.modelName!, value: m.modelName! }))
)

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
  editing.value = null
  selectedTools.value = []
  selectedMcp.value = []
}

async function handleSubmit() {
  if (!form.name.trim()) {
    toast.warning('请输入名称')
    return
  }
  submitting.value = true
  const payload: Agent = {
    ...form,
    tools: JSON.stringify(selectedTools.value),
    mcpServers: JSON.stringify(selectedMcp.value)
  }
  try {
    if (editing.value) { await updateAgent({ ...payload, id: editing.value.id }); toast.success('修改成功') }
    else { await addAgent(payload); toast.success('创建成功') }
    dialogVisible.value = false
    load()
  } catch { } finally { submitting.value = false }
}

async function handleDelete(id: number) {
  const ok = await confirm({ title: '删除确认', message: '确定删除该 Agent 吗？', confirmText: '确定删除', danger: true })
  if (!ok) return
  try { await deleteAgent(id); toast.success('删除成功'); load() } catch { }
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
