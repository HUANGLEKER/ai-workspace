<template>
  <RadioGroupRoot
    :model-value="String(modelValue)"
    class="flex items-center gap-4"
    @update:model-value="onChange"
  >
    <label
      v-for="opt in options"
      :key="String(opt.value)"
      class="flex cursor-pointer items-center gap-2 text-sm text-zinc-800"
    >
      <RadioGroupItem
        :value="String(opt.value)"
        class="flex h-4 w-4 items-center justify-center rounded-full border border-zinc-200/80 bg-white transition-all duration-200 ease-out focus:outline-none focus-visible:ring-2 focus-visible:ring-zinc-950 data-[state=checked]:border-zinc-900"
      >
        <RadioGroupIndicator class="h-2 w-2 rounded-full bg-zinc-900" />
      </RadioGroupItem>
      {{ opt.label }}
    </label>
  </RadioGroupRoot>
</template>

<script setup lang="ts">
import { RadioGroupRoot, RadioGroupItem, RadioGroupIndicator } from 'radix-vue'

const props = defineProps<{
  modelValue?: string | number
  options: { label: string; value: string | number }[]
  numeric?: boolean
}>()

const emit = defineEmits<{ 'update:modelValue': [string | number] }>()

function onChange(v: string) {
  emit('update:modelValue', props.numeric ? Number(v) : v)
}
</script>
