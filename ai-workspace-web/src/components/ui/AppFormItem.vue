<template>
  <div class="mb-4 last:mb-0">
    <label class="mb-1.5 block text-sm text-zinc-500">
      {{ label }}
      <span v-if="required" class="text-red-500">*</span>
    </label>
    <slot />
  </div>
</template>

<script setup lang="ts">
defineProps<{ label: string; required?: boolean }>()
</script>
