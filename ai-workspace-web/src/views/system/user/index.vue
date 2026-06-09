<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>用户管理</h2>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增用户</el-button>
    </div>

    <el-card shadow="never">
      <!-- 搜索栏 -->
      <div class="toolbar">
        <el-input
          v-model="searchUsername"
          placeholder="搜索用户名..."
          :prefix-icon="Search"
          clearable
          style="width: 220px"
          @keyup.enter="loadUsers"
        />
        <el-button type="primary" :icon="Search" @click="loadUsers">搜索</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </div>

      <el-table :data="users" v-loading="loading" stripe border style="margin-top:12px">
        <el-table-column label="ID" prop="id" width="70" align="center" />
        <el-table-column label="用户名" prop="username" width="130" />
        <el-table-column label="昵称" prop="nickname" width="130" />
        <el-table-column label="邮箱" prop="email" min-width="180" show-overflow-tooltip />
        <el-table-column label="手机号" prop="phone" width="130" align="center" />
        <el-table-column label="状态" prop="status" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              active-text="启用"
              inactive-text="禁用"
              inline-prompt
              @change="(v: string | number | boolean) => handleToggleStatus(row as SysUser, v as boolean)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="160" align="center">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link :icon="Edit" @click="openDialog(row as SysUser)">编辑</el-button>
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
        @change="loadUsers"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingUser ? '编辑用户' : '新增用户'"
      width="480px"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="!!editingUser" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item v-if="!editingUser" label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password clearable />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="请输入昵称" clearable />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱" clearable />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" clearable />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ editingUser ? '保存修改' : '确认添加' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

/**
 * 用户管理页（仅管理员）
 *
 * 功能：
 * 1. 用户分页查询（支持按用户名搜索）
 * 2. 新增/编辑/删除用户
 * 3. 启用/禁用用户（el-switch 直接切换，乐观更新本地状态）
 *
 * 编辑时用户名不可修改（后端不可变约定），密码字段留空则不更新。
 */
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Search, Edit, Delete } from '@element-plus/icons-vue'
import type { SysUser } from '@/types'
import { pageUsers, addUser, updateUser, deleteUser, toggleUserStatus } from '@/api/system'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const users = ref<SysUser[]>([])
const editingUser = ref<SysUser | null>(null)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchUsername = ref('')
const formRef = ref<FormInstance>()

const form = reactive<SysUser>({
  username: '', password: '', nickname: '', email: '', phone: '', status: 1
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' }]
}

onMounted(loadUsers)

async function loadUsers() {
  loading.value = true
  try {
    const res = await pageUsers({ page: page.value, size: pageSize.value, username: searchUsername.value || undefined })
    users.value = res.records
    total.value = res.total
  } catch {
    users.value = []
  } finally {
    loading.value = false
  }
}

function resetSearch() {
  searchUsername.value = ''
  page.value = 1
  loadUsers()
}

function openDialog(user?: SysUser) {
  editingUser.value = user || null
  if (user) {
    Object.assign(form, { ...user, password: '' })
  } else {
    Object.assign(form, { username: '', password: '', nickname: '', email: '', phone: '', status: 1 })
  }
  dialogVisible.value = true
}

function resetForm() {
  formRef.value?.resetFields()
  editingUser.value = null
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editingUser.value) {
      await updateUser(form)
      ElMessage.success('修改成功')
    } else {
      await addUser(form)
      ElMessage.success('添加成功')
    }
    dialogVisible.value = false
    loadUsers()
  } catch {
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除该用户吗？', '删除确认', {
    confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning'
  }).catch(() => { throw new Error('cancel') })
  try {
    await deleteUser(id)
    ElMessage.success('删除成功')
    loadUsers()
  } catch (e) {
    if ((e as Error).message !== 'cancel') ElMessage.error('删除失败')
  }
}

async function handleToggleStatus(row: SysUser, enabled: boolean) {
  const newStatus = enabled ? 1 : 0
  try {
    await toggleUserStatus(row.id!, newStatus)
    row.status = newStatus
    ElMessage.success(enabled ? '用户已启用' : '用户已禁用')
  } catch {
    // 拦截器处理
  }
}

const formatDate = (d: string) => d ? new Date(d).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-') : '—'
</script>

<style scoped>
.page-wrapper { padding-bottom: 20px; }
.toolbar { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.pagination { margin-top: 16px; justify-content: flex-end; }
</style>
