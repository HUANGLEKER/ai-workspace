<template>
  <button
    :type="nativeType"
    :disabled="disabled || loading"
    class="inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-xl text-sm font-medium transition-all duration-200 ease-out focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-zinc-950 disabled:cursor-not-allowed disabled:opacity-50"
    :class="[variantClass, sizeClass, block ? 'w-full' : '']"
  >
    <Loader2 v-if="loading" class="h-4 w-4 animate-spin" />
    <component :is="icon" v-else-if="icon" class="h-4 w-4" />
    <slot />
  </button>
</template>

<script setup lang="ts">
import { computed, type Component } from 'vue'
import { Loader2 } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'ghost' | 'danger' | 'danger-ghost'
    size?: 'sm' | 'md' | 'lg' | 'icon'
    icon?: Component
    loading?: boolean
    disabled?: boolean
    block?: boolean
    nativeType?: 'button' | 'submit'
  }>(),
  { variant: 'secondary', size: 'md', nativeType: 'button' }
)

const variantClass = computed(
  () =>
    ({
      primary: 'bg-zinc-900 text-white hover:bg-zinc-800',
      secondary: 'border border-zinc-200/80 bg-white text-zinc-800 hover:bg-zinc-50',
      ghost: 'text-zinc-500 hover:bg-zinc-100/50 hover:text-zinc-800',
      danger: 'bg-red-600 text-white hover:bg-red-500',
      'danger-ghost': 'text-red-500 hover:bg-red-50'
    })[props.variant]
)

const sizeClass = computed(
  () =>
    ({
      sm: 'h-8 px-3 text-xs',
      md: 'h-9 px-4',
      lg: 'h-10 px-5',
      icon: 'h-9 w-9 p-0'
    })[props.size]
)
</script>
