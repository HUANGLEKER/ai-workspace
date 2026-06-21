<template>
  <article
    class="flex h-full flex-col rounded-xl border border-zinc-200/80 bg-white p-4 shadow-[0_10px_30px_-10px_rgba(0,0,0,0.04)] transition-all duration-200 ease-out hover:shadow-md dark:border-zinc-800 dark:bg-zinc-900"
  >
    <div class="flex items-start gap-3">
      <div v-if="icon" class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-zinc-100 text-zinc-800 dark:bg-zinc-800 dark:text-zinc-100">
        <component :is="icon" class="h-5 w-5" />
      </div>
      <div class="min-w-0 flex-1">
        <h2 class="truncate text-sm font-semibold text-zinc-900 dark:text-zinc-100">{{ title }}</h2>
        <p v-if="description" class="mt-1 line-clamp-2 text-sm leading-6 text-zinc-500 dark:text-zinc-400">
          {{ description }}
        </p>
      </div>
    </div>

    <div v-if="$slots.meta" class="mt-4 flex flex-wrap gap-x-3 gap-y-1 text-xs text-zinc-400 dark:text-zinc-500">
      <slot name="meta" />
    </div>

    <div v-if="$slots.actions" class="mt-4 flex flex-wrap gap-2 border-t border-zinc-200/80 pt-3 dark:border-zinc-800">
      <slot name="actions" />
    </div>
  </article>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{
  title: string
  description?: string
  icon?: Component
}>()
</script>
