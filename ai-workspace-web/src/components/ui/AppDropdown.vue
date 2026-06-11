<template>
  <DropdownMenuRoot>
    <DropdownMenuTrigger as-child>
      <slot />
    </DropdownMenuTrigger>
    <DropdownMenuPortal>
      <DropdownMenuContent
        :side-offset="6"
        align="end"
        class="z-[8500] min-w-[160px] rounded-2xl border border-zinc-200/80 bg-white p-1.5 shadow-md transition-all duration-200 ease-out"
      >
        <template v-for="(item, idx) in items" :key="item.key">
          <div v-if="item.divided && idx > 0" class="my-1 h-px bg-zinc-200/80" />
          <DropdownMenuItem
            class="flex cursor-pointer items-center gap-2 rounded-xl px-3 py-2 text-sm outline-none transition-all duration-200 ease-out data-[highlighted]:bg-zinc-100/50"
            :class="item.danger ? 'text-red-500' : 'text-zinc-800'"
            @select="$emit('select', item.key)"
          >
            <component :is="item.icon" v-if="item.icon" class="h-4 w-4" :class="item.danger ? '' : 'text-zinc-500'" />
            {{ item.label }}
          </DropdownMenuItem>
        </template>
      </DropdownMenuContent>
    </DropdownMenuPortal>
  </DropdownMenuRoot>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import { DropdownMenuRoot, DropdownMenuTrigger, DropdownMenuPortal, DropdownMenuContent, DropdownMenuItem } from 'radix-vue'

export interface DropdownItem {
  key: string
  label: string
  icon?: Component
  danger?: boolean
  divided?: boolean
}

defineProps<{ items: DropdownItem[] }>()
defineEmits<{ select: [string] }>()
</script>
