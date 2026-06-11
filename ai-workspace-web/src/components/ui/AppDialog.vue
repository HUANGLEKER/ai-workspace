<template>
  <DialogRoot :open="modelValue" @update:open="onOpenChange">
    <DialogPortal>
      <DialogOverlay class="fixed inset-0 z-[8000] bg-zinc-950/30 transition-all duration-200 ease-out" />
      <DialogContent
        class="fixed left-1/2 top-1/2 z-[8001] flex max-h-[85vh] w-full -translate-x-1/2 -translate-y-1/2 flex-col rounded-2xl border border-zinc-200/80 bg-white shadow-md transition-all duration-200 ease-out focus:outline-none"
        :style="{ maxWidth: width }"
        @open-auto-focus.prevent
      >
        <div class="flex items-center justify-between border-b border-zinc-200/80 px-6 py-4">
          <DialogTitle class="text-base font-semibold text-zinc-800">{{ title }}</DialogTitle>
          <DialogClose
            class="rounded-xl p-1.5 text-zinc-500 transition-all duration-200 ease-out hover:bg-zinc-100/50 hover:text-zinc-800"
          >
            <X class="h-4 w-4" />
          </DialogClose>
        </div>
        <div class="flex-1 overflow-y-auto px-6 py-5">
          <slot />
        </div>
        <div v-if="$slots.footer" class="flex justify-end gap-2 border-t border-zinc-200/80 px-6 py-4">
          <slot name="footer" />
        </div>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>

<script setup lang="ts">
import { DialogRoot, DialogPortal, DialogOverlay, DialogContent, DialogTitle, DialogClose } from 'radix-vue'
import { X } from 'lucide-vue-next'

withDefaults(defineProps<{ modelValue: boolean; title?: string; width?: string }>(), { width: '520px' })

const emit = defineEmits<{ 'update:modelValue': [boolean]; close: [] }>()

function onOpenChange(open: boolean) {
  emit('update:modelValue', open)
  if (!open) emit('close')
}
</script>
