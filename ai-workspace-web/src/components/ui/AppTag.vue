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
      default: 'bg-zinc-100/50 text-zinc-500 border border-zinc-200/80',
      success: 'bg-emerald-50 text-emerald-600 border border-emerald-200/80',
      warning: 'bg-amber-50 text-amber-600 border border-amber-200/80',
      danger: 'bg-red-50 text-red-600 border border-red-200/80',
      info: 'bg-zinc-50 text-zinc-500 border border-zinc-200/80'
    })[props.variant]
)
</script>
