<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>提示词中心</h2>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新建提示词</el-button>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索标题..." :prefix-icon="Search" clearable
          style="width: 220px" @keyup.enter="load" />
        <el-input v-model="category" placeholder="按分类筛选" clearable style="width: 160px" @keyup.enter="load" />
        <el-button type="primary" :icon="Search" @click="load">搜索</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <div v-loading="loading" class="prompt-grid">
        <el-card v-for="p in prompts" :key="p.id" class="prompt-card" shadow="hover">
          <div class="prompt-card-head">
            <span class="prompt-title">{{ p.title }}</span>
            <el-tag v-if="p.category" size="small" type="info">{{ p.category }}</el-tag>
          </div>
          <p class="prompt-desc">{{ p.description || '—' }}</p>
          <div class="prompt-content">{{ p.content }}</div>
          <div class="prompt-actions">
            <el-button link type="primary" :icon="CopyDocument" @click="copy(p.content)">复制</el-button>
            <el-button link :icon="Edit" @click="openDialog(p)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(p.id!)">删除</el-button>
          </div>
        </el-card>
      </div>
      <el-empty v-if="!loading && prompts.length === 0" description="还没有提示词，点击右上角新建" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑提示词' : '新建提示词'" width="560px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入标题" clearable />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="form.category" placeholder="如：写作 / 编程" clearable />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="一句话描述用途" clearable />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="6" placeholder="提示词正文，可用 {变量} 占位" />
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
 * 提示词中心页
 *
 * 功能：
 * 1. 提示词列表（支持标题关键词和分类筛选）
 * 2. 新建/编辑/删除提示词
 * 3. 一键复制提示词内容到剪贴板
 */
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Search, Edit, Delete, CopyDocument } from '@element-plus/icons-vue'
import { listPrompts, addPrompt, updatePrompt, deletePrompt, type Prompt } from '@/api/prompt'

const loading = ref(false)
const submitting = ref(false)
const prompts = ref<Prompt[]>([])
const keyword = ref('')
const category = ref('')
const dialogVisible = ref(false)
const editing = ref<Prompt | null>(null)
const formRef = ref<FormInstance>()

const form = reactive<Prompt>({ title: '', content: '', category: '', description: '' })
const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
}

onMounted(load)

async function load() {
  loading.value = true
  try {
    prompts.value = await listPrompts({ keyword: keyword.value || undefined, category: category.value || undefined })
  } catch { prompts.value = [] } finally { loading.value = false }
}
function reset() {
  keyword.value = ''
  category.value = ''
  load()
}

function openDialog(row?: Prompt) {
  editing.value = row || null
  if (row) Object.assign(form, row)
  else Object.assign(form, { title: '', content: '', category: '', description: '' })
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
    if (editing.value) { await updatePrompt({ ...form, id: editing.value.id }); ElMessage.success('修改成功') }
    else { await addPrompt(form); ElMessage.success('创建成功') }
    dialogVisible.value = false
    load()
  } catch { } finally { submitting.value = false }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除该提示词吗？', '删除确认', { type: 'warning' }).catch(() => { throw new Error('cancel') })
  try { await deletePrompt(id); ElMessage.success('删除成功'); load() } catch { }
}

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动选择复制')
  }
}
</script>

<style scoped>
.page-wrapper { padding-bottom: 20px; }
.toolbar { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin-bottom: 16px; }
.prompt-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.prompt-card-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.prompt-title { font-weight: 600; color: #303133; }
.prompt-desc { color: #909399; font-size: 13px; margin: 6px 0; }
.prompt-content {
  background: #f5f7fa; border-radius: 6px; padding: 10px; font-size: 13px; color: #606266;
  white-space: pre-wrap; word-break: break-word; max-height: 120px; overflow: auto;
}
.prompt-actions { margin-top: 10px; text-align: right; }
</style>
