<template>
  <div class="pb-6">
    <div class="mb-4 flex items-start justify-between gap-4">
      <h2 class="text-lg font-semibold text-zinc-800 dark:text-zinc-100">MCP 服务器</h2>
      <AppButton variant="primary" :icon="Plus" @click="openDialog()">新建 MCP 服务器</AppButton>
    </div>

    <!-- MCP 服务器卡片网格 -->
    <div class="relative grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
      <div
        v-for="s in servers"
        :key="s.id"
        class="flex flex-col rounded-2xl border border-zinc-200/80 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-5 shadow-[0_10px_30px_-10px_rgba(0,0,0,0.04)] transition-all duration-200 ease-out hover:shadow-md"
      >
        <div class="mb-2 flex items-center gap-3">
          <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-zinc-100 dark:bg-zinc-800/50">
            <Plug class="h-5 w-5 text-zinc-800 dark:text-zinc-100" />
          </div>
          <div class="min-w-0 flex-1">
            <div class="truncate text-sm font-semibold text-zinc-800 dark:text-zinc-100">{{ s.name }}</div>
            <div class="mt-0.5 flex items-center gap-1.5">
              <AppTag :variant="s.transport === 'sse' ? 'info' : 'warning'">{{ s.transport }}</AppTag>
              <AppTag :variant="s.enabled === 1 ? 'success' : 'info'">{{ s.enabled === 1 ? '启用' : '禁用' }}</AppTag>
            </div>
          </div>
        </div>
        <p class="line-clamp-1 text-sm text-zinc-500 dark:text-zinc-400">{{ s.description || '—' }}</p>
        <p class="mt-1.5 truncate rounded-xl bg-zinc-50 dark:bg-zinc-950 px-3 py-1.5 text-xs text-zinc-500 dark:text-zinc-400">
          {{ s.transport === 'sse' ? s.url : s.command }}
        </p>
        <div class="mt-4 flex gap-1 border-t border-zinc-200/80 dark:border-zinc-800 pt-3">
          <AppButton size="sm" variant="ghost" :icon="Zap" :loading="testingId === s.id" @click="handleTest(s.id!)">测试</AppButton>
          <AppButton size="sm" variant="ghost" :icon="Pencil" @click="openDialog(s)">编辑</AppButton>
          <AppButton size="sm" variant="danger-ghost" :icon="Trash2" @click="handleDelete(s.id!)">删除</AppButton>
        </div>
      </div>

      <AppEmpty v-if="!loading && servers.length === 0" class="col-span-full py-16" description="还没有 MCP 服务器，点击右上角新建" :icon="Plug" />
      <AppLoading v-if="loading" overlay />
    </div>

    <AppDialog v-model="dialogVisible" :title="editing ? '编辑 MCP 服务器' : '新建 MCP 服务器'" width="560px" @close="resetForm">
      <AppFormItem label="名称" required>
        <AppInput v-model="form.name" placeholder="服务器名称" />
      </AppFormItem>
      <AppFormItem label="描述">
        <AppInput v-model="form.description" placeholder="用途说明" />
      </AppFormItem>
      <AppFormItem label="传输方式">
        <AppRadioGroup v-model="form.transport" :options="[{ label: 'SSE', value: 'sse' }, { label: 'stdio', value: 'stdio' }]" />
      </AppFormItem>
      <AppFormItem v-if="form.transport === 'sse'" label="服务地址">
        <AppInput v-model="form.url" placeholder="https://host/sse" />
      </AppFormItem>
      <AppFormItem v-else label="启动命令">
        <AppInput v-model="form.command" placeholder="如：npx -y @modelcontextprotocol/server-xxx" />
      </AppFormItem>
      <AppFormItem label="配置">
        <AppTextarea v-model="form.config" :rows="4" placeholder="可选：JSON 配置（headers/env/args）" />
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
 * MCP 服务器注册表页：Card Layout 展示，CRUD（sse/stdio 两种传输方式），
 * 连通性测试（仅 sse 类型可与 Agent 集成）。
 */
import { ref, reactive, onMounted } from 'vue'
import { Plus, Plug, Zap, Pencil, Trash2 } from 'lucide-vue-next'
import {
  listMcpServers, addMcpServer, updateMcpServer, deleteMcpServer, testMcpServer, type McpServer
} from '@/api/mcp'
import {
  AppButton, AppDialog, AppFormItem, AppInput, AppTextarea, AppRadioGroup, AppTag,
  AppEmpty, AppLoading, toast, confirm
} from '@/components/ui'

const loading = ref(false)
const submitting = ref(false)
const testingId = ref<number | null>(null)
const servers = ref<McpServer[]>([])
const dialogVisible = ref(false)
const editing = ref<McpServer | null>(null)

const form = reactive<McpServer>({ name: '', description: '', transport: 'sse', url: '', command: '', config: '', enabled: 1 })

onMounted(load)

async function load() {
  loading.value = true
  try { servers.value = await listMcpServers() } catch { servers.value = [] } finally { loading.value = false }
}

function openDialog(row?: McpServer) {
  editing.value = row || null
  if (row) Object.assign(form, row)
  else Object.assign(form, { name: '', description: '', transport: 'sse', url: '', command: '', config: '', enabled: 1 })
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
    if (editing.value) { await updateMcpServer({ ...form, id: editing.value.id }); toast.success('修改成功') }
    else { await addMcpServer(form); toast.success('创建成功') }
    dialogVisible.value = false
    load()
  } catch { } finally { submitting.value = false }
}

async function handleDelete(id: number) {
  const ok = await confirm({ title: '删除确认', message: '确定删除该 MCP 服务器吗？', confirmText: '确定删除', danger: true })
  if (!ok) return
  try { await deleteMcpServer(id); toast.success('删除成功'); load() } catch { }
}

async function handleTest(id: number) {
  testingId.value = id
  try {
    const res = await testMcpServer(id)
    if (res.reachable) {
      toast.success(`连通正常（${res.latency}ms）：${res.message}`)
    } else {
      toast.warning(`连通失败：${res.message}`)
    }
  } catch { } finally { testingId.value = null }
}
</script>
