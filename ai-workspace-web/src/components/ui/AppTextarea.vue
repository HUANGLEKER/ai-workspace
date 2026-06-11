<template>
  <textarea
    ref="el"
    :value="modelValue"
    :placeholder="placeholder"
    :rows="rows"
    :disabled="disabled"
    class="w-full resize-none rounded-xl border border-zinc-200/80 bg-white px-3 py-2.5 text-sm leading-relaxed text-zinc-800 placeholder:text-zinc-400 transition-all duration-200 ease-out focus:outline-none focus:ring-2 focus:ring-zinc-950 disabled:cursor-not-allowed disabled:bg-zinc-50 disabled:text-zinc-500 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100 dark:placeholder:text-zinc-500 dark:focus:ring-zinc-300 dark:disabled:bg-zinc-900"
    :style="autoGrow ? { minHeight: '48px', maxHeight: '200px', overflowY: 'auto' } : undefined"
    @input="onInput"
    @keydown="$emit('keydown', $event)"
  />
</template>

<script setup lang="ts">
import { ref, nextTick, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue?: string
    placeholder?: string
    rows?: number
    disabled?: boolean
    autoGrow?: boolean
  }>(),
  { modelValue: '', rows: 3 }
)

const emit = defineEmits<{ 'update:modelValue': [string]; keydown: [KeyboardEvent] }>()

const el = ref<HTMLTextAreaElement>()

function resize() {
  if (!props.autoGrow || !el.value) return
  el.value.style.height = 'auto'
  el.value.style.height = Math.min(el.value.scrollHeight, 200) + 'px'
}

function onInput(e: Event) {
  emit('update:modelValue', (e.target as HTMLTextAreaElement).value)
  resize()
}

// 外部清空内容（如发送后）时同步收起高度
watch(
  () => props.modelValue,
  () => nextTick(resize)
)
</script>
