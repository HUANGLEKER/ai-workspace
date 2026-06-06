<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>模型管理</h2>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增模型</el-button>
    </div>

    <el-card shadow="never">
      <!-- 搜索栏 -->
      <div class="toolbar">
        <el-input
          v-model="searchName"
          placeholder="搜索模型名称..."
          :prefix-icon="Search"
          clearable
          style="width: 220px"
          @keyup.enter="loadModels"
        />
        <el-button type="primary" :icon="Search" @click="loadModels">搜索</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>

      <el-table :data="models" v-loading="loading" stripe border style="margin-top:12px">
        <el-table-column label="ID" prop="id" width="70" align="center" />
        <el-table-column label="模型名称" prop="modelName" min-width="160" show-overflow-tooltip />
        <el-table-column label="提供商" prop="provider" width="130" />
        <el-table-column label="API 地址" prop="apiUrl" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.apiUrl || '默认' }}</template>
        </el-table-column>
        <el-table-column label="状态" prop="enabled" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled === 1"
              active-text="启用"
              inactive-text="禁用"
              inline-prompt
              @change="(v: string | number | boolean) => handleToggleStatus(row as ChatModel, v as boolean)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="160" align="center">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link :icon="Edit" @click="openDialog(row as ChatModel)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 0"
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        class="pagination"
        @change="loadModels"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑模型' : '新增模型'"
      width="480px"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="form.modelName" placeholder="如 gpt-4o、deepseek-chat" clearable />
        </el-form-item>
        <el-form-item label="提供商" prop="provider">
          <el-input v-model="form.provider" placeholder="如 OpenAI、DeepSeek、Ollama" clearable />
        </el-form-item>
        <el-form-item label="API 地址" prop="apiUrl">
          <el-input v-model="form.apiUrl" placeholder="留空则使用服务端默认配置" clearable />
        </el-form-item>
        <el-form-item label="API Key" prop="apiKey">
          <el-input
            v-model="form.apiKey"
            type="password"
            :placeholder="editing ? '留空则不修改' : '留空则使用服务端默认配置'"
            show-password
            clearable
          />
        </el-form-item>
        <el-form-item label="状态" prop="enabled">
          <el-radio-group v-model="form.enabled">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ editing ? '保存修改' : '确认添加' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Search, Edit, Delete } from '@element-plus/icons-vue'
import type { ChatModel } from '@/types'
import { pageModels, addModel, updateModel, deleteModel, toggleModelStatus } from '@/api/chat'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const models = ref<ChatModel[]>([])
const editing = ref<ChatModel | null>(null)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchName = ref('')
const formRef = ref<FormInstance>()

const emptyForm = (): ChatModel => ({ modelName: '', provider: '', apiUrl: '', apiKey: '', enabled: 1 })
const form = reactive<ChatModel>(emptyForm())

const rules: FormRules = {
  modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
  provider: [{ required: true, message: '请输入提供商', trigger: 'blur' }]
}

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
  Object.assign(form, model ? { ...model, apiKey: '' } : emptyForm())
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
    if (editing.value) {
      await updateModel(form)
      ElMessage.success('修改成功')
    } else {
      await addModel(form)
      ElMessage.success('添加成功')
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
  await ElMessageBox.confirm('确定删除该模型吗？', '删除确认', {
    confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning'
  }).catch(() => { throw new Error('cancel') })
  try {
    await deleteModel(id)
    ElMessage.success('删除成功')
    loadModels()
  } catch (e) {
    if ((e as Error).message !== 'cancel') ElMessage.error('删除失败')
  }
}

async function handleToggleStatus(row: ChatModel, enabled: boolean) {
  const newStatus = enabled ? 1 : 0
  try {
    await toggleModelStatus(row.id!, newStatus)
    row.enabled = newStatus
    ElMessage.success(enabled ? '模型已启用' : '模型已禁用')
  } catch {
    // 拦截器处理
  }
}

const formatDate = (d?: string) => d ? new Date(d).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-') : '—'
</script>

<style scoped>
.page-wrapper { padding-bottom: 20px; }
.toolbar { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.pagination { margin-top: 16px; justify-content: flex-end; }
</style>
