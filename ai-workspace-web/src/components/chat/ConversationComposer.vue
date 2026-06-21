<template>
  <!--
    输入区（Chat / RAG 共用）：模型选择 + 联网开关 + 自增高文本框 + 发送/停止 + 提示词选择器。
    键盘交互（/ 唤起选择器、上下/回车导航、Enter 发送、Shift+Enter 换行）在此内聚；
    业务态（pickerOpen/pickerQuery 计算、发送逻辑）仍由父页传入与处理。
  -->
  <div class="relative shrink-0 border-t border-line bg-surface px-4 py-3">
    <PromptPicker ref="pickerRef" :open="pickerOpen" :query="pickerQuery" @use="(t, s) => emit('prompt-use', t, s)" />
    <div class="mb-2 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <AppSelect
          :model-value="selectedModel"
          :options="modelOptions"
          :placeholder="modelPlaceholder"
          class="!w-44 max-w-44"
          @update:model-value="emit('update:selectedModel', $event as string)"
        />
        <button
          type="button"
          :disabled="streaming"
          class="flex items-center gap-1.5 rounded-lg border px-2.5 py-1.5 text-xs font-medium transition-all duration-200 ease-out disabled:opacity-50"
          :class="webSearch
            ? 'border-primary bg-primary text-primary-fg'
            : 'border-zinc-200/80 text-zinc-500 hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-400 dark:hover:bg-zinc-800'"
          :title="webSearch ? '联网搜索已开启' : '联网搜索已关闭'"
          @click="emit('update:webSearch', !webSearch)"
        >
          <Globe class="h-3.5 w-3.5" />
          联网
        </button>
      </div>
      <span class="text-xs text-zinc-400 dark:text-zinc-500">{{ hint }}</span>
    </div>
    <div class="flex items-end gap-2.5">
      <AppTextarea
        :model-value="modelValue"
        :rows="1"
        auto-grow
        :placeholder="placeholder"
        @update:model-value="emit('update:modelValue', $event)"
        @keydown="onKeydown"
      />
      <button
        v-if="streaming"
        class="flex h-12 shrink-0 items-center gap-1.5 rounded-xl bg-red-600 px-5 text-sm text-white transition-all duration-200 ease-out hover:bg-red-500"
        @click="emit('stop')"
      >
        <CircleStop class="h-4 w-4" />
        停止
      </button>
      <button
        v-else
        class="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-primary text-primary-fg transition-all duration-200 ease-out hover:bg-primary-hover"
        :class="modelValue.trim() ? '' : 'pointer-events-none opacity-50'"
        @click="emit('send')"
      >
        <Send class="h-4 w-4" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Send, CircleStop, Globe } from 'lucide-vue-next'
import { AppSelect, AppTextarea, type SelectOption } from '@/components/ui'
import PromptPicker from './PromptPicker.vue'

const props = defineProps<{
  modelValue: string
  streaming: boolean
  selectedModel: string
  modelOptions: SelectOption[]
  webSearch: boolean
  placeholder: string
  hint: string
  modelPlaceholder?: string
  pickerOpen: boolean
  pickerQuery: string
}>()

const emit = defineEmits<{
  'update:modelValue': [string]
  'update:selectedModel': [string]
  'update:webSearch': [boolean]
  send: []
  stop: []
  'prompt-use': [string, boolean]
}>()

const pickerRef = ref<InstanceType<typeof PromptPicker> | null>(null)

function onKeydown(e: KeyboardEvent) {
  if (props.pickerOpen) {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault()
      pickerRef.value?.moveActive(e.key === 'ArrowDown' ? 1 : -1)
      return
    }
    if (e.key === 'Enter' && !e.isComposing) {
      e.preventDefault()
      pickerRef.value?.chooseActive()
      return
    }
    if (e.key === 'Escape') {
      e.preventDefault()
      emit('update:modelValue', '')
      return
    }
  }
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    emit('send')
  }
}
</script>
