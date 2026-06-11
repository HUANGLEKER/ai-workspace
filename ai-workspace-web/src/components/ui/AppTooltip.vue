<template>
  <TooltipRoot>
    <TooltipTrigger as-child>
      <slot />
    </TooltipTrigger>
    <TooltipPortal>
      <TooltipContent
        :side="side"
        :side-offset="6"
        class="z-[9500] rounded-xl bg-zinc-900 px-2.5 py-1.5 text-xs text-white shadow-md transition-all duration-200 ease-out"
      >
        {{ content }}
      </TooltipContent>
    </TooltipPortal>
  </TooltipRoot>
</template>

<script setup lang="ts">
import { TooltipRoot, TooltipTrigger, TooltipPortal, TooltipContent } from 'radix-vue'

withDefaults(defineProps<{ content: string; side?: 'top' | 'bottom' | 'left' | 'right' }>(), { side: 'top' })
</script>
