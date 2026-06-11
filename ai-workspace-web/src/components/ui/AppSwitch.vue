<template>
  <SwitchRoot
    :checked="modelValue"
    :disabled="disabled"
    class="relative inline-flex h-5 w-9 shrink-0 cursor-pointer items-center rounded-full transition-all duration-200 ease-out focus:outline-none focus-visible:ring-2 focus-visible:ring-zinc-950 disabled:cursor-not-allowed disabled:opacity-50"
    :class="modelValue ? 'bg-zinc-900' : 'bg-zinc-200'"
    @update:checked="$emit('update:modelValue', $event); $emit('change', $event)"
  >
    <SwitchThumb
      class="block h-4 w-4 rounded-full bg-white shadow-sm transition-all duration-200 ease-out data-[state=checked]:translate-x-[18px] data-[state=unchecked]:translate-x-0.5"
    />
  </SwitchRoot>
</template>

<script setup lang="ts">
import { SwitchRoot, SwitchThumb } from 'radix-vue'

defineProps<{ modelValue: boolean; disabled?: boolean }>()
defineEmits<{ 'update:modelValue': [boolean]; change: [boolean] }>()
</script>
