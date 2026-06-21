<template>
  <div
    class="rounded-2xl border border-line bg-surface shadow-[0_10px_30px_-10px_rgba(0,0,0,0.04)] transition-all duration-200 ease-out"
    :class="hover ? 'hover:shadow-md' : ''"
  >
    <div v-if="title || $slots.header || $slots.extra" class="flex items-center justify-between border-b border-line px-5 py-3.5">
      <slot name="header">
        <span class="text-sm font-semibold text-zinc-800 dark:text-zinc-100">{{ title }}</span>
      </slot>
      <slot name="extra" />
    </div>
    <div :class="padded ? 'p-5' : ''">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ title?: string; hover?: boolean; padded?: boolean }>(), { padded: true })
</script>
