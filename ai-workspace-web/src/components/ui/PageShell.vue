<template>
  <!--
    页面外壳：纯语义容器，仅负责 max-width / gap / 纵向 flex 布局。
    滚动与外边距由 layout/index.vue 的 <main> 统一负责（非 fullPage 页 overflow-y-auto + p-6），
    此处不再自带 padding / overflow，避免「双层滚动 / 双层 padding」。
  -->
  <section class="flex min-h-full flex-col">
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
  }>(),
  {
    maxWidth: 'wide',
    gap: 'md'
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
