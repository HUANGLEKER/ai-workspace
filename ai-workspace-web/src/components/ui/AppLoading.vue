<template>
  <div
    v-if="overlay"
    class="absolute inset-0 z-10 flex items-center justify-center rounded-xl bg-white/60"
  >
    <Loader2 class="h-6 w-6 animate-spin text-zinc-500" />
  </div>
  <div v-else class="flex items-center justify-center gap-2 py-10 text-sm text-zinc-500">
    <Loader2 class="h-5 w-5 animate-spin" />
    加载中...
  </div>
</template>

<script setup lang="ts">
import { Loader2 } from 'lucide-vue-next'

defineProps<{ overlay?: boolean }>()
</script>
