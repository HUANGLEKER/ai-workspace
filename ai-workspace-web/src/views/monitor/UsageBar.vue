<template>
  <div class="flex items-center gap-3">
    <div class="h-2 flex-1 overflow-hidden rounded-full bg-zinc-100 dark:bg-zinc-800">
      <div
        class="h-full rounded-full transition-all duration-200 ease-out"
        :class="percent < 70 ? 'bg-zinc-900 dark:bg-zinc-100' : percent < 90 ? 'bg-amber-500' : 'bg-red-500'"
        :style="{ width: percent + '%' }"
      />
    </div>
    <span class="w-10 text-right text-sm text-zinc-800 dark:text-zinc-100">{{ percent }}%</span>
  </div>
</template>

<script setup lang="ts">
defineProps<{ percent: number }>()
</script>
