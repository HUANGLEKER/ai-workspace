<template>
  <div
    class="flex flex-col gap-3 rounded-xl border border-zinc-200/80 bg-white p-3 shadow-[0_10px_30px_-10px_rgba(0,0,0,0.04)] dark:border-zinc-800 dark:bg-zinc-900 sm:flex-row sm:items-center sm:justify-between"
  >
    <div v-if="$slots.default" class="flex min-w-0 flex-1 flex-wrap items-center gap-2">
      <slot />
    </div>
    <div v-if="$slots.actions" class="flex shrink-0 flex-wrap items-center gap-2">
      <slot name="actions" />
    </div>
  </div>
</template>
