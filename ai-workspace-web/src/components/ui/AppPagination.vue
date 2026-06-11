<template>
  <div class="flex items-center justify-end gap-3 text-sm text-zinc-500">
    <span>共 {{ total }} 条</span>
    <AppSelect
      :model-value="size"
      :options="sizeOptions"
      numeric
      class="!w-28"
      @change="onSizeChange"
    />
    <div class="flex items-center gap-1">
      <button
        class="flex h-8 w-8 items-center justify-center rounded-xl border border-zinc-200/80 bg-white text-zinc-500 transition-all duration-200 ease-out hover:bg-zinc-50 disabled:cursor-not-allowed disabled:opacity-50"
        :disabled="page <= 1"
        @click="go(page - 1)"
      >
        <ChevronLeft class="h-4 w-4" />
      </button>
      <span class="px-2 text-zinc-800">{{ page }} / {{ pageCount }}</span>
      <button
        class="flex h-8 w-8 items-center justify-center rounded-xl border border-zinc-200/80 bg-white text-zinc-500 transition-all duration-200 ease-out hover:bg-zinc-50 disabled:cursor-not-allowed disabled:opacity-50"
        :disabled="page >= pageCount"
        @click="go(page + 1)"
      >
        <ChevronRight class="h-4 w-4" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'
import AppSelect from './AppSelect.vue'

const props = withDefaults(defineProps<{ page: number; size: number; total: number; sizes?: number[] }>(), {
  sizes: () => [10, 20, 50]
})

const emit = defineEmits<{ 'update:page': [number]; 'update:size': [number]; change: [] }>()

const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
const sizeOptions = computed(() => props.sizes.map((s) => ({ label: `${s} 条/页`, value: s })))

function go(p: number) {
  emit('update:page', p)
  emit('change')
}

function onSizeChange(v: string | number) {
  emit('update:size', Number(v))
  emit('update:page', 1)
  emit('change')
}
</script>
