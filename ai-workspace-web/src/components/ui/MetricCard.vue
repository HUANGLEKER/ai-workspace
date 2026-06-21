<template>
  <div
    class="rounded-xl border border-zinc-200/80 bg-white p-4 shadow-[0_10px_30px_-10px_rgba(0,0,0,0.04)] dark:border-zinc-800 dark:bg-zinc-900"
  >
    <div class="flex items-start justify-between gap-3">
      <div class="min-w-0">
        <p class="truncate text-sm text-zinc-500 dark:text-zinc-400">{{ label }}</p>
        <div class="mt-2 truncate text-2xl font-semibold tracking-tight text-zinc-900 dark:text-zinc-100">
          {{ value }}
        </div>
      </div>
      <div v-if="icon" class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-200">
        <component :is="icon" class="h-4.5 w-4.5" />
      </div>
    </div>
    <p v-if="hint" class="mt-3 truncate text-xs text-zinc-400 dark:text-zinc-500">{{ hint }}</p>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{
  label: string
  value: number | string
  hint?: string
  icon?: Component
}>()
</script>
