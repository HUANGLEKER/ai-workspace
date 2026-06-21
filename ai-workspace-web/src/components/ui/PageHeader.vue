<template>
  <div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
    <div class="flex min-w-0 items-start gap-3">
      <div
        v-if="icon"
        class="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary text-primary-fg"
      >
        <component :is="icon" class="h-5 w-5" />
      </div>
      <div class="min-w-0">
        <h1 class="truncate text-xl font-semibold text-zinc-900 dark:text-zinc-100">{{ title }}</h1>
        <p v-if="description" class="mt-1 max-w-3xl text-sm leading-6 text-zinc-500 dark:text-zinc-400">
          {{ description }}
        </p>
      </div>
    </div>

    <div v-if="$slots.actions" class="flex shrink-0 items-center gap-2">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{
  title: string
  description?: string
  icon?: Component
}>()
</script>
