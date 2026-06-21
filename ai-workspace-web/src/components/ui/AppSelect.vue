<template>
  <SelectRoot
    :model-value="modelValue === undefined || modelValue === '' ? undefined : String(modelValue)"
    :disabled="disabled"
    @update:model-value="onSelect"
  >
    <SelectTrigger
      class="flex h-9 w-full items-center justify-between gap-2 rounded-xl border border-zinc-200/80 bg-white px-3 text-sm text-zinc-800 transition-all duration-200 ease-out focus:outline-none focus:border-primary focus:ring-2 focus:ring-primary/30 disabled:cursor-not-allowed disabled:bg-zinc-50 data-[placeholder]:text-zinc-400 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100 dark:disabled:bg-zinc-900"
    >
      <SelectValue :placeholder="placeholder" class="truncate" />
      <ChevronDown class="h-4 w-4 shrink-0 text-zinc-400" />
    </SelectTrigger>
    <SelectPortal>
      <SelectContent
        position="popper"
        :side-offset="6"
        class="z-[8800] max-h-64 w-[var(--radix-select-trigger-width)] overflow-y-auto rounded-2xl border border-line bg-surface p-1.5 shadow-md"
      >
        <SelectViewport>
          <SelectItem
            v-for="opt in options"
            :key="String(opt.value)"
            :value="String(opt.value)"
            class="flex cursor-pointer items-center justify-between rounded-xl px-3 py-2 text-sm text-zinc-800 outline-none transition-all duration-200 ease-out data-[highlighted]:bg-zinc-100/50 dark:text-zinc-100 dark:data-[highlighted]:bg-zinc-800"
          >
            <SelectItemText class="truncate">{{ opt.label }}</SelectItemText>
            <SelectItemIndicator>
              <Check class="h-4 w-4 text-primary" />
            </SelectItemIndicator>
          </SelectItem>
          <div v-if="options.length === 0" class="px-3 py-2 text-sm text-zinc-400 dark:text-zinc-500">暂无选项</div>
        </SelectViewport>
      </SelectContent>
    </SelectPortal>
  </SelectRoot>
</template>

<script setup lang="ts">
import {
  SelectRoot, SelectTrigger, SelectValue, SelectPortal, SelectContent,
  SelectViewport, SelectItem, SelectItemText, SelectItemIndicator
} from 'radix-vue'
import { ChevronDown, Check } from 'lucide-vue-next'

export interface SelectOption {
  label: string
  value: string | number
}

const props = defineProps<{
  modelValue?: string | number
  options: SelectOption[]
  placeholder?: string
  disabled?: boolean
  // 选项 value 为数字时设置，回传前转回 number
  numeric?: boolean
}>()

const emit = defineEmits<{ 'update:modelValue': [string | number]; change: [string | number] }>()

function onSelect(v: string) {
  const value = props.numeric ? Number(v) : v
  emit('update:modelValue', value)
  emit('change', value)
}
</script>
