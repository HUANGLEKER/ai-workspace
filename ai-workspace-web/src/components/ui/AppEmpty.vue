<template>
  <div class="flex flex-col items-center justify-center gap-3 py-10 text-center">
    <slot name="icon">
      <component :is="icon || Inbox" class="h-10 w-10 text-zinc-300 dark:text-zinc-600" />
    </slot>
    <p class="text-sm text-zinc-500 dark:text-zinc-400">{{ description }}</p>
    <slot />
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import { Inbox } from 'lucide-vue-next'

withDefaults(defineProps<{ description?: string; icon?: Component }>(), { description: '暂无数据' })
</script>
