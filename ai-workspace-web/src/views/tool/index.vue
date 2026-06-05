<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>工具中心</h2>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新建工具</el-button>
    </div>

    <el-card shadow="never">
      <el-table :data="tools" v-loading="loading" stripe border>
        <el-table-column label="ID" prop="id" width="70" align="center" />
        <el-table-column label="名称" prop="name" width="160" />
        <el-table-column label="类型" prop="toolType" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.toolType === 'http' ? '' : 'success'">{{ row.toolType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="描述" prop="description" min-width="180" show-overflow-tooltip />
        <el-table-column label="调用地址" prop="endpoint" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled === 1 ? 'success' : 'info'" size="small">
              {{ row.enabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link :icon="Edit" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && tools.length === 0" description="还没有工具，点击右上角新建" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑工具' : '新建工具'" width="560px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="工具唯一名称，供 Agent 引用" clearable />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.toolType">
            <el-radio value="http">HTTP</el-radio>
            <el-radio value="builtin">内置</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="工具用途说明" clearable />
        </el-form-item>
        <el-form-item v-if="form.toolType === 'http'" label="调用地址">
          <el-input v-model="form.endpoint" placeholder="https://..." clearable />
        </el-form-item>
        <el-form-item label="参数Schema">
          <el-input v-model="form.config" type="textarea" :rows="4" placeholder="可选：JSON 参数定义" />
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

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { listTools, addTool, updateTool, deleteTool, type Tool } from '@/api/tool'

const loading = ref(false)
const submitting = ref(false)
const tools = ref<Tool[]>([])
const dialogVisible = ref(false)
const editing = ref<Tool | null>(null)
const formRef = ref<FormInstance>()

const form = reactive<Tool>({ name: '', description: '', toolType: 'http', endpoint: '', config: '', enabled: 1 })
const rules: FormRules = { name: [{ required: true, message: '请输入名称', trigger: 'blur' }] }

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
  formRef.value?.resetFields()
  editing.value = null
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editing.value) { await updateTool({ ...form, id: editing.value.id }); ElMessage.success('修改成功') }
    else { await addTool(form); ElMessage.success('创建成功') }
    dialogVisible.value = false
    load()
  } catch { } finally { submitting.value = false }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除该工具吗？', '删除确认', { type: 'warning' }).catch(() => { throw new Error('cancel') })
  try { await deleteTool(id); ElMessage.success('删除成功'); load() } catch { }
}
</script>

<style scoped>
.page-wrapper { padding-bottom: 20px; }
</style>
