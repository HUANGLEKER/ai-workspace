<template>
  <span
    class="inline-flex items-center rounded-xl px-2 py-0.5 text-xs font-medium"
    :class="variantClass"
  >
    <slot />
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{ variant?: 'default' | 'success' | 'warning' | 'danger' | 'info' }>(),
  { variant: 'default' }
)

const variantClass = computed(
  () =>
    ({
      default: 'bg-zinc-100/50 text-zinc-500 border border-zinc-200/80 dark:bg-zinc-800 dark:text-zinc-400 dark:border-zinc-700',
      success: 'bg-emerald-50 text-emerald-600 border border-emerald-200/80 dark:bg-emerald-950/40 dark:text-emerald-400 dark:border-emerald-900',
      warning: 'bg-amber-50 text-amber-600 border border-amber-200/80 dark:bg-amber-950/40 dark:text-amber-400 dark:border-amber-900',
      danger: 'bg-red-50 text-red-600 border border-red-200/80 dark:bg-red-950/40 dark:text-red-400 dark:border-red-900',
      info: 'bg-zinc-50 text-zinc-500 border border-zinc-200/80 dark:bg-zinc-800 dark:text-zinc-400 dark:border-zinc-700'
    })[props.variant]
)
</script>
