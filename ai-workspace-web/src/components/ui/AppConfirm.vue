<template>
  <DialogRoot :open="confirmState.visible" @update:open="(v) => !v && settle(false)">
    <DialogPortal>
      <DialogOverlay class="fixed inset-0 z-[9000] bg-zinc-950/30 transition-all duration-200 ease-out" />
      <DialogContent
        class="fixed left-1/2 top-1/2 z-[9001] w-[400px] max-w-[calc(100vw-2rem)] -translate-x-1/2 -translate-y-1/2 rounded-2xl border border-zinc-200/80 bg-white p-6 shadow-md transition-all duration-200 ease-out focus:outline-none dark:border-zinc-800 dark:bg-zinc-900"
      >
        <DialogTitle class="flex items-center gap-2 text-base font-semibold text-zinc-800 dark:text-zinc-100">
          <AlertTriangle v-if="confirmState.danger" class="h-5 w-5 text-amber-500" />
          {{ confirmState.title }}
        </DialogTitle>
        <DialogDescription class="mt-3 text-sm leading-relaxed text-zinc-500 dark:text-zinc-400">
          {{ confirmState.message }}
        </DialogDescription>
        <div class="mt-6 flex justify-end gap-2">
          <button
            v-if="!confirmState.alertOnly"
            class="rounded-xl border border-zinc-200/80 bg-white px-4 py-2 text-sm text-zinc-800 transition-all duration-200 ease-out hover:bg-zinc-50 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100 dark:hover:bg-zinc-700"
            @click="settle(false)"
          >
            {{ confirmState.cancelText }}
          </button>
          <button
            class="rounded-xl px-4 py-2 text-sm text-white transition-all duration-200 ease-out"
            :class="confirmState.danger ? 'bg-red-600 hover:bg-red-500' : 'bg-zinc-900 hover:bg-zinc-800 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-200'"
            @click="settle(true)"
          >
            {{ confirmState.confirmText }}
          </button>
        </div>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>

<script setup lang="ts">
import { DialogRoot, DialogPortal, DialogOverlay, DialogContent, DialogTitle, DialogDescription } from 'radix-vue'
import { AlertTriangle } from 'lucide-vue-next'
import { confirmState, settle } from './confirm'
</script>
