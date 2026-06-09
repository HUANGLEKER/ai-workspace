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

/**
 * 顶部 Header 组件
 *
 * 功能：
 * 1. 根据当前路由 meta.title 显示面包屑
 * 2. 展示登录用户昵称/用户名
 * 3. 提供退出登录入口（先调接口使服务端 token 失效，再清除本地 Store）
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { QuestionFilled, UserFilled, User, Lock, SwitchButton, ArrowDown } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { logout } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// 从路由 meta 读取页面标题，用于面包屑展示
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
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 var(--space-6);
  background: var(--bg-container);
  border-bottom: 1px solid var(--border-color);
  box-shadow: var(--shadow-sm);
}

.header-left {
  display: flex;
  align-items: center;
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.user-info {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  cursor: pointer;
  padding: 4px var(--space-2);
  border-radius: var(--radius-sm);
  transition: background 0.2s;
}

.user-info:hover {
  background: var(--bg-subtle);
}

.user-avatar {
  background: var(--brand-primary);
}

.username {
  font-size: 14px;
  color: var(--text-primary);
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
