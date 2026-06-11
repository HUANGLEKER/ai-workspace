<template>
  <div class="relative w-full">
    <component
      :is="icon"
      v-if="icon"
      class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400"
    />
    <input
      :value="modelValue"
      :type="type"
      :placeholder="placeholder"
      :disabled="disabled"
      class="h-9 w-full rounded-xl border border-zinc-200/80 bg-white text-sm text-zinc-800 placeholder:text-zinc-400 transition-all duration-200 ease-out focus:outline-none focus:ring-2 focus:ring-zinc-950 disabled:cursor-not-allowed disabled:bg-zinc-50 disabled:text-zinc-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100 dark:placeholder:text-zinc-500 dark:focus:ring-zinc-300 dark:disabled:bg-zinc-900"
      :class="icon ? 'pl-9 pr-3' : 'px-3'"
      @input="$emit('update:modelValue', ($event.target as HTMLInputElement).value)"
      @keyup.enter="$emit('enter')"
    />
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

withDefaults(
  defineProps<{
    modelValue?: string
    type?: string
    placeholder?: string
    disabled?: boolean
    icon?: Component
  }>(),
  { modelValue: '', type: 'text' }
)

defineEmits<{ 'update:modelValue': [string]; enter: [] }>()
</script>
