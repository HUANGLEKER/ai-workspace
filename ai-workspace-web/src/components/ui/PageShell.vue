<template>
  <section class="flex min-h-full flex-col" :class="[padded ? 'p-6' : '', scroll ? 'overflow-y-auto' : 'overflow-hidden']">
    <div class="mx-auto flex w-full flex-1 flex-col" :class="[maxWidthClass, gapClass]">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    maxWidth?: 'full' | 'wide' | 'content'
    gap?: 'sm' | 'md' | 'lg'
    padded?: boolean
    scroll?: boolean
  }>(),
  {
    maxWidth: 'wide',
    gap: 'md',
    padded: false,
    scroll: false
  }
)

const maxWidthClass = computed(
  () =>
    ({
      full: 'max-w-none',
      wide: 'max-w-[1440px]',
      content: 'max-w-[1120px]'
    })[props.maxWidth]
)

const gapClass = computed(
  () =>
    ({
      sm: 'gap-3',
      md: 'gap-4',
      lg: 'gap-6'
    })[props.gap]
)
</script>
