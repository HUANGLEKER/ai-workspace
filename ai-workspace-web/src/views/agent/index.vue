<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>Agent</h2>
      <el-button type="primary" :icon="Plus" disabled>新建 Agent（即将上线）</el-button>
    </div>
    <el-card shadow="never">
      <el-empty description="Agent 功能正在开发中，敬请期待">
        <template #image>
          <el-icon size="80" color="#c0c4cc"><Cpu /></el-icon>
        </template>
        <el-tag type="warning">开发中 · Sprint 5</el-tag>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue'
</script>

<style scoped>
.page-wrapper { padding-bottom: 20px; }
</style>
