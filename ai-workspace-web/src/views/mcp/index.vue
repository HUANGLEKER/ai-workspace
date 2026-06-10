<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>MCP 服务器</h2>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新建 MCP 服务器</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="servers" v-loading="loading" stripe border>
        <el-table-column label="ID" prop="id" width="70" align="center" />
        <el-table-column label="名称" prop="name" width="150" />
        <el-table-column label="传输" prop="transport" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.transport === 'sse' ? 'info' : 'warning'">{{ row.transport }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="地址 / 命令" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">{{ row.transport === 'sse' ? row.url : row.command }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Connection" :loading="testingId === row.id"
              @click="handleTest(row.id)">测试</el-button>
            <el-button link :icon="Edit" @click="openDialog(row as McpServer)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && servers.length === 0" description="还没有 MCP 服务器，点击右上角新建" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑 MCP 服务器' : '新建 MCP 服务器'" width="560px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="服务器名称" clearable />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="用途说明" clearable />
        </el-form-item>
        <el-form-item label="传输方式">
          <el-radio-group v-model="form.transport">
            <el-radio value="sse">SSE</el-radio>
            <el-radio value="stdio">stdio</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.transport === 'sse'" label="服务地址">
          <el-input v-model="form.url" placeholder="https://host/sse" clearable />
        </el-form-item>
        <el-form-item v-else label="启动命令">
          <el-input v-model="form.command" placeholder="如：npx -y @modelcontextprotocol/server-xxx" clearable />
        </el-form-item>
        <el-form-item label="配置">
          <el-input v-model="form.config" type="textarea" :rows="4" placeholder="可选：JSON 配置（headers/env/args）" />
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
  </div>
</template>

/**
 * MCP 服务器注册表页
 *
 * 功能：
 * 1. MCP 服务器 CRUD（支持 sse/stdio 两种传输方式）
 * 2. 连通性测试（仅 sse 类型，探测 HTTP 可达性）
 *
 * sse 服务器在 Agent 运行时通过 langchain-mcp-adapters 动态加载工具列表，
 * 因此只有 sse 类型才能与 Agent 集成使用。
 */
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Edit, Delete, Connection } from '@element-plus/icons-vue'
import {
  listMcpServers, addMcpServer, updateMcpServer, deleteMcpServer, testMcpServer, type McpServer
} from '@/api/mcp'

const loading = ref(false)
const submitting = ref(false)
const testingId = ref<number | null>(null)
const servers = ref<McpServer[]>([])
const dialogVisible = ref(false)
const editing = ref<McpServer | null>(null)
const formRef = ref<FormInstance>()

const form = reactive<McpServer>({ name: '', description: '', transport: 'sse', url: '', command: '', config: '', enabled: 1 })
const rules: FormRules = { name: [{ required: true, message: '请输入名称', trigger: 'blur' }] }

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
  formRef.value?.resetFields()
  editing.value = null
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editing.value) { await updateMcpServer({ ...form, id: editing.value.id }); ElMessage.success('修改成功') }
    else { await addMcpServer(form); ElMessage.success('创建成功') }
    dialogVisible.value = false
    load()
  } catch { } finally { submitting.value = false }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除该 MCP 服务器吗？', '删除确认', { type: 'warning' }).catch(() => { throw new Error('cancel') })
  try { await deleteMcpServer(id); ElMessage.success('删除成功'); load() } catch { }
}

async function handleTest(id: number) {
  testingId.value = id
  try {
    const res = await testMcpServer(id)
    if (res.reachable) {
      ElMessage.success(`连通正常（${res.latency}ms）：${res.message}`)
    } else {
      ElMessage.warning(`连通失败：${res.message}`)
    }
  } catch { } finally { testingId.value = null }
}
</script>

<style scoped>
.page-wrapper { padding-bottom: 20px; }
</style>
