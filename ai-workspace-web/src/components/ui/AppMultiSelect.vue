<template>
  <DropdownMenuRoot>
    <DropdownMenuTrigger as-child>
      <button
        type="button"
        class="flex h-auto min-h-9 w-full items-center justify-between gap-2 rounded-xl border border-zinc-200/80 bg-white px-3 py-1.5 text-sm transition-all duration-200 ease-out focus:outline-none focus:ring-2 focus:ring-zinc-950"
      >
        <div class="flex flex-1 flex-wrap items-center gap-1">
          <span v-if="modelValue.length === 0" class="text-zinc-400">{{ placeholder }}</span>
          <span
            v-for="v in modelValue"
            :key="v"
            class="inline-flex items-center gap-1 rounded-xl border border-zinc-200/80 bg-zinc-50 px-2 py-0.5 text-xs text-zinc-800"
          >
            {{ v }}
            <X class="h-3 w-3 cursor-pointer text-zinc-400 hover:text-zinc-800" @click.stop="toggle(v)" />
          </span>
        </div>
        <ChevronDown class="h-4 w-4 shrink-0 text-zinc-400" />
      </button>
    </DropdownMenuTrigger>
    <DropdownMenuPortal>
      <DropdownMenuContent
        :side-offset="6"
        align="start"
        class="z-[8800] max-h-64 w-[var(--radix-dropdown-menu-trigger-width)] overflow-y-auto rounded-2xl border border-zinc-200/80 bg-white p-1.5 shadow-md"
      >
        <DropdownMenuCheckboxItem
          v-for="opt in options"
          :key="opt"
          :checked="modelValue.includes(opt)"
          class="flex cursor-pointer items-center justify-between rounded-xl px-3 py-2 text-sm text-zinc-800 outline-none transition-all duration-200 ease-out data-[highlighted]:bg-zinc-100/50"
          @select.prevent="toggle(opt)"
        >
          <span class="truncate">{{ opt }}</span>
          <Check v-if="modelValue.includes(opt)" class="h-4 w-4 text-zinc-800" />
        </DropdownMenuCheckboxItem>
        <div v-if="options.length === 0" class="px-3 py-2 text-sm text-zinc-400">暂无选项</div>
      </DropdownMenuContent>
    </DropdownMenuPortal>
  </DropdownMenuRoot>
</template>

<script setup lang="ts">
import { DropdownMenuRoot, DropdownMenuTrigger, DropdownMenuPortal, DropdownMenuContent, DropdownMenuCheckboxItem } from 'radix-vue'
import { ChevronDown, Check, X } from 'lucide-vue-next'

const props = withDefaults(defineProps<{ modelValue: string[]; options: string[]; placeholder?: string }>(), {
  placeholder: '请选择'
})

const emit = defineEmits<{ 'update:modelValue': [string[]] }>()

function toggle(v: string) {
  const next = props.modelValue.includes(v)
    ? props.modelValue.filter((x) => x !== v)
    : [...props.modelValue, v]
  emit('update:modelValue', next)
}
</script>
