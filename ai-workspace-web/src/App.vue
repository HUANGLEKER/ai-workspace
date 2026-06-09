<template>
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
/**
 * 根组件
 *
 * 仅负责挂载 el-config-provider 以设置 Element Plus 中文语言包。
 * 原先通过 app.use(ElementPlus, { locale }) 全局配置，
 * 迁移至按需引入后改由此处的 el-config-provider 统一注入。
 */
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>
