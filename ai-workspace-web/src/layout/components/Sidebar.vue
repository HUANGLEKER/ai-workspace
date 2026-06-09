<template>
  <div class="sidebar" :class="{ collapsed }">
    <!-- Logo -->
    <div class="sidebar-logo">
      <el-icon class="logo-icon" size="24"><Cpu /></el-icon>
      <span v-if="!collapsed" class="logo-text">AI Workspace</span>
    </div>

    <!-- 导航菜单 -->
    <el-menu
      :default-active="activeMenu"
      :collapse="collapsed"
      :collapse-transition="false"
      background-color="#1d2535"
      text-color="#bfcbd9"
      active-text-color="#ffffff"
      router
      class="sidebar-menu"
    >
      <el-menu-item index="/dashboard">
        <el-icon><Odometer /></el-icon>
        <template #title>仪表盘</template>
      </el-menu-item>

      <el-menu-item index="/chat">
        <el-icon><ChatDotRound /></el-icon>
        <template #title>AI 对话</template>
      </el-menu-item>

      <el-sub-menu index="knowledge">
        <template #title>
          <el-icon><Reading /></el-icon>
          <span>知识库</span>
        </template>
        <el-menu-item index="/knowledge/base">
          <el-icon><Collection /></el-icon>
          <template #title>知识库管理</template>
        </el-menu-item>
<el-menu-item index="/knowledge/rag">
          <el-icon><Search /></el-icon>
          <template #title>知识库问答</template>
        </el-menu-item>
      </el-sub-menu>

      <el-menu-item index="/file">
        <el-icon><Folder /></el-icon>
        <template #title>文件中心</template>
      </el-menu-item>

      <el-menu-item index="/prompt">
        <el-icon><MagicStick /></el-icon>
        <template #title>提示词中心</template>
      </el-menu-item>

      <el-menu-item index="/workflow">
        <el-icon><Connection /></el-icon>
        <template #title>工作流</template>
      </el-menu-item>

      <el-menu-item index="/agent">
        <el-icon><Cpu /></el-icon>
        <template #title>Agent</template>
      </el-menu-item>

      <el-menu-item index="/tool">
        <el-icon><Tools /></el-icon>
        <template #title>工具中心</template>
      </el-menu-item>

      <el-menu-item index="/mcp">
        <el-icon><Link /></el-icon>
        <template #title>MCP 服务器</template>
      </el-menu-item>

      <el-menu-item v-if="authStore.isAdmin" index="/monitor">
        <el-icon><Monitor /></el-icon>
        <template #title>监控</template>
      </el-menu-item>

      <el-sub-menu v-if="authStore.isAdmin" index="system">
        <template #title>
          <el-icon><Setting /></el-icon>
          <span>系统管理</span>
        </template>
        <el-menu-item index="/system/user">
          <el-icon><User /></el-icon>
          <template #title>用户管理</template>
        </el-menu-item>
        <el-menu-item index="/system/model">
          <el-icon><Cpu /></el-icon>
          <template #title>模型管理</template>
        </el-menu-item>
        <el-menu-item index="/system/job">
          <el-icon><Timer /></el-icon>
          <template #title>定时任务</template>
        </el-menu-item>
      </el-sub-menu>
    </el-menu>

    <!-- 折叠按钮 -->
    <div class="sidebar-collapse" @click="$emit('toggle')">
      <el-icon size="16">
        <DArrowLeft v-if="!collapsed" />
        <DArrowRight v-else />
      </el-icon>
    </div>
  </div>
</template>

/**
 * 侧边导航栏组件
 *
 * 功能：
 * 1. 展示全局导航菜单，支持折叠/展开
 * 2. 根据当前路由高亮激活菜单项
 * 3. 管理员专属菜单（监控、系统管理）通过 isAdmin 守卫隐藏，防止普通用户看到入口
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

defineProps<{ collapsed: boolean }>()
defineEmits<{ toggle: [] }>()

const route = useRoute()
const authStore = useAuthStore()
// el-menu 的 router 模式下，index 值需与路由 path 完全匹配
const activeMenu = computed(() => route.path)
</script>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  width: 220px;
  height: 100%;
  background-color: #1d2535;
  transition: width 0.3s;
  flex-shrink: 0;
  position: relative;
}

.sidebar.collapsed {
  width: 64px;
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  height: 60px;
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  border-bottom: 1px solid #2d3748;
  overflow: hidden;
  white-space: nowrap;
}

.logo-icon {
  flex-shrink: 0;
  color: var(--brand-primary);
}

.sidebar-menu {
  flex: 1;
  border-right: none;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 8px 0;
}

.sidebar-menu :deep(.el-menu-item),
.sidebar-menu :deep(.el-sub-menu__title) {
  height: 48px;
  line-height: 48px;
  position: relative;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background-color: rgba(64, 158, 255, 0.18) !important;
  color: #fff !important;
}

/* active 项左侧高亮条 */
.sidebar-menu :deep(.el-menu-item.is-active)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background-color: var(--brand-primary);
}

.sidebar-menu :deep(.el-menu-item:hover),
.sidebar-menu :deep(.el-sub-menu__title:hover) {
  background-color: #2d3748 !important;
}

.sidebar-menu :deep(.el-sub-menu__title) {
  color: #bfcbd9;
}

/* 子菜单内项缩进时高亮条对齐左边缘 */
.sidebar-menu :deep(.el-menu .el-menu-item) {
  min-width: auto;
}

.sidebar-collapse {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 44px;
  color: #bfcbd9;
  cursor: pointer;
  border-top: 1px solid #2d3748;
  transition: background-color 0.2s;
}

.sidebar-collapse:hover {
  background-color: #2d3748;
  color: #fff;
}
</style>
