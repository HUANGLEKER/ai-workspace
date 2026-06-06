<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>知识库管理</h2>
      <el-button type="primary" :icon="Plus" @click="openCreateDialog">新建知识库</el-button>
    </div>

    <!-- 知识库列表 -->
    <div v-loading="loading" class="kb-grid">
      <el-card
        v-for="kb in knowledgeBases"
        :key="kb.id"
        class="kb-card"
        shadow="hover"
      >
        <div class="kb-card-body">
          <div class="kb-icon">
            <el-icon size="28" color="#409EFF"><Reading /></el-icon>
          </div>
          <div class="kb-info">
            <div class="kb-name">{{ kb.kbName }}</div>
            <div class="kb-desc">{{ kb.description || '暂无描述' }}</div>
            <div class="kb-meta">
              <span>创建人：{{ kb.createBy }}</span>
              <span>{{ formatDate(kb.createTime) }}</span>
            </div>
          </div>
        </div>
        <div class="kb-actions">
          <el-button size="small" :icon="Document" @click="goDocuments(kb)">文档管理</el-button>
          <el-button size="small" :icon="Edit" @click="openEditDialog(kb)">编辑</el-button>
          <el-button size="small" type="danger" :icon="Delete" @click="handleDelete(kb.id)">删除</el-button>
        </div>
      </el-card>

      <el-empty v-if="!loading && knowledgeBases.length === 0" description="暂无知识库，点击右上角新建" />
    </div>

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingKb ? '编辑知识库' : '新建知识库'"
      width="480px"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="kbName">
          <el-input v-model="form.kbName" placeholder="请输入知识库名称" clearable />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入知识库描述（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ editingKb ? '保存修改' : '确认创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Document, Edit, Delete } from '@element-plus/icons-vue'
import type { KnowledgeBase } from '@/types'
import { listKnowledgeBases, createKnowledgeBase, updateKnowledgeBase, deleteKnowledgeBase } from '@/api/kb'

const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const editingKb = ref<KnowledgeBase | null>(null)
const knowledgeBases = ref<KnowledgeBase[]>([])
const formRef = ref<FormInstance>()

const form = reactive({ kbName: '', description: '' })
const rules: FormRules = {
  kbName: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }]
}

const formatDate = (d: string) => d ? new Date(d).toLocaleDateString('zh-CN') : ''

onMounted(loadList)

async function loadList() {
  loading.value = true
  try {
    knowledgeBases.value = await listKnowledgeBases()
  } catch {
    knowledgeBases.value = []
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  editingKb.value = null
  dialogVisible.value = true
}

function openEditDialog(kb: KnowledgeBase) {
  editingKb.value = kb
  form.kbName = kb.kbName
  form.description = kb.description || ''
  dialogVisible.value = true
}

function resetForm() {
  form.kbName = ''
  form.description = ''
  editingKb.value = null
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editingKb.value) {
      await updateKnowledgeBase({ ...editingKb.value, ...form })
      ElMessage.success('修改成功')
    } else {
      await createKnowledgeBase(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadList()
  } catch {
    // 由拦截器处理
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除该知识库吗？删除后相关文档也将一并删除。', '删除确认', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).catch(() => { throw new Error('cancel') })
  try {
    await deleteKnowledgeBase(id)
    ElMessage.success('删除成功')
    loadList()
  } catch (e) {
    if ((e as Error).message !== 'cancel') ElMessage.error('删除失败')
  }
}

function goDocuments(kb: KnowledgeBase) {
  router.push({ path: '/knowledge/document', query: { kbId: kb.id, kbName: kb.kbName } })
}
</script>

<style scoped>
.page-wrapper {
  padding-bottom: 20px;
}

.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.kb-card {
  border-radius: 8px;
  transition: transform 0.2s;
}

.kb-card:hover {
  transform: translateY(-2px);
}

.kb-card-body {
  display: flex;
  gap: 14px;
  margin-bottom: 14px;
}

.kb-icon {
  width: 48px;
  height: 48px;
  background: #ecf5ff;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.kb-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.kb-desc {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.kb-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #c0c4cc;
}

.kb-actions {
  display: flex;
  gap: 8px;
  border-top: 1px solid #f0f2f5;
  padding-top: 12px;
}
</style>
