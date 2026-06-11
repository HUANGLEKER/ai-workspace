<template>
  <div
    class="flex shrink-0 items-center justify-center rounded-xl"
    :class="[sizeClass, variant === 'dark' ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900' : 'bg-zinc-100 text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400']"
  >
    <component :is="icon" v-if="icon" :class="iconSizeClass" />
    <span v-else class="text-sm font-medium">{{ text?.slice(0, 1).toUpperCase() }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'

const props = withDefaults(
  defineProps<{ icon?: Component; text?: string; size?: 'sm' | 'md' | 'lg'; variant?: 'dark' | 'light' }>(),
  { size: 'md', variant: 'light' }
)

const sizeClass = computed(() => ({ sm: 'h-7 w-7', md: 'h-8 w-8', lg: 'h-10 w-10' })[props.size])
const iconSizeClass = computed(() => ({ sm: 'h-3.5 w-3.5', md: 'h-4 w-4', lg: 'h-5 w-5' })[props.size])
</script>
