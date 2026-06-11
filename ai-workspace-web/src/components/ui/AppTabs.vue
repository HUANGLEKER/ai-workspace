<template>
  <TabsRoot
    :model-value="modelValue"
    @update:model-value="(v) => { $emit('update:modelValue', String(v)); $emit('change', String(v)) }"
  >
    <TabsList class="inline-flex items-center gap-1 rounded-xl bg-zinc-100/50 p-1 dark:bg-zinc-800">
      <TabsTrigger
        v-for="tab in tabs"
        :key="tab.name"
        :value="tab.name"
        class="rounded-xl px-4 py-1.5 text-sm text-zinc-500 transition-all duration-200 ease-out data-[state=active]:bg-white data-[state=active]:text-zinc-800 data-[state=active]:shadow-sm focus:outline-none dark:text-zinc-400 dark:data-[state=active]:bg-zinc-900 dark:data-[state=active]:text-zinc-100"
      >
        {{ tab.label }}
      </TabsTrigger>
    </TabsList>
    <TabsContent v-for="tab in tabs" :key="tab.name" :value="tab.name" class="mt-4 focus:outline-none">
      <slot :name="tab.name" />
    </TabsContent>
  </TabsRoot>
</template>

<script setup lang="ts">
import { TabsRoot, TabsList, TabsTrigger, TabsContent } from 'radix-vue'

defineProps<{ modelValue: string; tabs: { name: string; label: string }[] }>()
defineEmits<{ 'update:modelValue': [string]; change: [string] }>()
</script>
