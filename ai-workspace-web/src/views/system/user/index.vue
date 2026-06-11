<template>
  <div class="pb-6">
    <div class="mb-4 flex items-start justify-between gap-4">
      <h2 class="text-lg font-semibold text-zinc-800 dark:text-zinc-100">用户管理</h2>
      <AppButton variant="primary" :icon="Plus" @click="openDialog()">新增用户</AppButton>
    </div>

    <AppCard>
      <!-- 搜索栏 -->
      <div class="mb-4 flex flex-wrap items-center gap-3">
        <AppSearch v-model="searchUsername" placeholder="搜索用户名..." class="!w-56 max-w-56" @enter="loadUsers" />
        <AppButton variant="primary" :icon="Search" @click="loadUsers">搜索</AppButton>
        <AppButton @click="resetSearch">重置</AppButton>
      </div>

      <AppTable :columns="columns" :data="users" :loading="loading">
        <template #cell-status="{ row }">
          <AppSwitch
            :model-value="row.status === 1"
            @change="(v: boolean) => handleToggleStatus(row, v)"
          />
        </template>
        <template #cell-createTime="{ row }">{{ formatDate(row.createTime!) }}</template>
        <template #cell-actions="{ row }">
          <div class="flex justify-center gap-1">
            <AppButton size="sm" variant="ghost" :icon="Pencil" @click="openDialog(row)">编辑</AppButton>
            <AppButton size="sm" variant="danger-ghost" :icon="Trash2" @click="handleDelete(row.id!)">删除</AppButton>
          </div>
        </template>
      </AppTable>

      <AppPagination
        v-if="total > 0"
        v-model:page="page"
        v-model:size="pageSize"
        :total="total"
        class="mt-4"
        @change="loadUsers"
      />
    </AppCard>

    <!-- 新增/编辑弹窗 -->
    <AppDialog v-model="dialogVisible" :title="editingUser ? '编辑用户' : '新增用户'" width="480px" @close="resetForm">
      <AppFormItem label="用户名" required>
        <AppInput v-model="form.username" :disabled="!!editingUser" placeholder="请输入用户名" />
      </AppFormItem>
      <AppFormItem v-if="!editingUser" label="密码" required>
        <AppInput v-model="form.password" type="password" placeholder="请输入密码（至少6位）" />
      </AppFormItem>
      <AppFormItem label="昵称" required>
        <AppInput v-model="form.nickname" placeholder="请输入昵称" />
      </AppFormItem>
      <AppFormItem label="邮箱">
        <AppInput v-model="form.email" placeholder="请输入邮箱" />
      </AppFormItem>
      <AppFormItem label="备注">
        <AppInput v-model="form.remark" placeholder="请输入备注" />
      </AppFormItem>
      <AppFormItem label="状态">
        <AppRadioGroup v-model="form.status" numeric :options="[{ label: '启用', value: 1 }, { label: '禁用', value: 0 }]" />
      </AppFormItem>
      <template #footer>
        <AppButton @click="dialogVisible = false">取消</AppButton>
        <AppButton variant="primary" :loading="submitting" @click="handleSubmit">
          {{ editingUser ? '保存修改' : '确认添加' }}
        </AppButton>
      </template>
    </AppDialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 用户管理页（仅管理员）：分页查询（按用户名搜索）、新增/编辑/删除、启用/禁用。
 * 编辑时用户名不可修改（后端不可变约定），密码字段留空则不更新。
 */
import { ref, reactive, onMounted } from 'vue'
import { Plus, Search, Pencil, Trash2 } from 'lucide-vue-next'
import type { SysUser } from '@/types'
import { pageUsers, addUser, updateUser, deleteUser, toggleUserStatus } from '@/api/system'
import {
  AppButton, AppCard, AppSearch, AppTable, AppSwitch, AppDialog, AppFormItem, AppInput,
  AppRadioGroup, AppPagination, toast, confirm, type TableColumn
} from '@/components/ui'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const users = ref<SysUser[]>([])
const editingUser = ref<SysUser | null>(null)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchUsername = ref('')

const columns: TableColumn[] = [
  { key: 'id', label: 'ID', width: '70px', align: 'center' },
  { key: 'username', label: '用户名', width: '130px' },
  { key: 'nickname', label: '昵称', width: '130px' },
  { key: 'email', label: '邮箱' },
  { key: 'remark', label: '备注' },
  { key: 'status', label: '状态', width: '90px', align: 'center' },
  { key: 'createTime', label: '创建时间', width: '170px', align: 'center' },
  { key: 'actions', label: '操作', width: '160px', align: 'center' }
]

const form = reactive<SysUser>({
  username: '', password: '', nickname: '', email: '', remark: '', status: 1
})

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
    Object.assign(form, { username: '', password: '', nickname: '', email: '', remark: '', status: 1 })
  }
  dialogVisible.value = true
}

function resetForm() {
  editingUser.value = null
}

async function handleSubmit() {
  if (!form.username.trim() || !form.nickname?.trim()) {
    toast.warning('请填写用户名和昵称')
    return
  }
  if (!editingUser.value && (!form.password || form.password.length < 6)) {
    toast.warning('密码至少6位')
    return
  }
  if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
    toast.warning('请输入有效的邮箱地址')
    return
  }
  submitting.value = true
  try {
    if (editingUser.value) {
      await updateUser(form)
      toast.success('修改成功')
    } else {
      await addUser(form)
      toast.success('添加成功')
    }
    dialogVisible.value = false
    loadUsers()
  } catch {
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: number) {
  const ok = await confirm({
    title: '删除确认', message: '确定删除该用户吗？', confirmText: '确定删除', danger: true
  })
  if (!ok) return
  try {
    await deleteUser(id)
    toast.success('删除成功')
    loadUsers()
  } catch {
    toast.error('删除失败')
  }
}

async function handleToggleStatus(row: SysUser, enabled: boolean) {
  const newStatus = enabled ? 1 : 0
  try {
    await toggleUserStatus(row.id!, newStatus)
    row.status = newStatus
    toast.success(enabled ? '用户已启用' : '用户已禁用')
  } catch {
    // 拦截器处理
  }
}

const formatDate = (d: string) => d ? new Date(d).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-') : '—'
</script>
