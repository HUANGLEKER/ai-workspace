<template>
  <TooltipProvider :delay-duration="300">
    <router-view />
    <AppToaster />
    <AppConfirm />
  </TooltipProvider>
</template>

<script setup lang="ts">
// 根组件：注入 Radix Tooltip 上下文，并挂载全局 Toast 与 Confirm 单例
import { TooltipProvider } from 'radix-vue'
import AppToaster from '@/components/ui/AppToaster.vue'
import AppConfirm from '@/components/ui/AppConfirm.vue'
</script>
