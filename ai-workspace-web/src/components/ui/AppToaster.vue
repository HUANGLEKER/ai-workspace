<template>
  <Teleport to="body">
    <div class="pointer-events-none fixed left-1/2 top-4 z-[9999] flex w-full max-w-sm -translate-x-1/2 flex-col items-center gap-2">
      <TransitionGroup
        enter-active-class="transition-all duration-200 ease-out"
        enter-from-class="opacity-0 -translate-y-2"
        leave-active-class="transition-all duration-200 ease-out"
        leave-to-class="opacity-0 -translate-y-2"
      >
        <div
          v-for="item in toastState.items"
          :key="item.id"
          class="pointer-events-auto flex items-center gap-2 rounded-xl border border-zinc-200/80 bg-white px-4 py-2.5 text-sm text-zinc-800 shadow-md dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-100"
        >
          <CheckCircle2 v-if="item.type === 'success'" class="h-4 w-4 shrink-0 text-emerald-500" />
          <XCircle v-else-if="item.type === 'error'" class="h-4 w-4 shrink-0 text-red-500" />
          <AlertTriangle v-else-if="item.type === 'warning'" class="h-4 w-4 shrink-0 text-amber-500" />
          <Info v-else class="h-4 w-4 shrink-0 text-zinc-500 dark:text-zinc-400" />
          <span class="break-words">{{ item.message }}</span>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { CheckCircle2, XCircle, AlertTriangle, Info } from 'lucide-vue-next'
import { toastState } from './toast'
</script>
