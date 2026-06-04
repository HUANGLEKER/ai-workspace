<template>
  <div class="header">
    <div class="header-left">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <div class="header-right">
      <el-tooltip content="帮助文档" placement="bottom">
        <el-button link :icon="QuestionFilled" circle />
      </el-tooltip>

      <el-dropdown @command="handleCommand" trigger="click">
        <div class="user-info">
          <el-avatar :size="32" :icon="UserFilled" class="user-avatar" />
          <span class="username">{{ authStore.userInfo?.nickname || authStore.userInfo?.username || '用户' }}</span>
          <el-icon><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile" :icon="User">个人信息</el-dropdown-item>
            <el-dropdown-item command="password" :icon="Lock">修改密码</el-dropdown-item>
            <el-dropdown-item divided command="logout" :icon="SwitchButton">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { QuestionFilled, UserFilled, User, Lock, SwitchButton, ArrowDown } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { logout } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const currentTitle = computed(() => route.meta.title as string || '')

const handleCommand = async (cmd: string) => {
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确定要退出登录吗？', '退出确认', {
      confirmButtonText: '确定退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
    try { await logout() } catch {}
    authStore.logout()
    router.push('/login')
  } else if (cmd === 'profile') {
    ElMessageBox.alert('个人信息功能即将上线', '提示', { confirmButtonText: '知道了' })
  } else if (cmd === 'password') {
    ElMessageBox.alert('修改密码功能即将上线', '提示', { confirmButtonText: '知道了' })
  }
}
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.2s;
}

.user-info:hover {
  background: #f5f7fa;
}

.username {
  font-size: 14px;
  color: #303133;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
