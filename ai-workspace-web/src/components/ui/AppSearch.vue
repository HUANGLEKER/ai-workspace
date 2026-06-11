<template>
  <AppInput
    :model-value="modelValue"
    :icon="Search"
    :placeholder="placeholder"
    @update:model-value="$emit('update:modelValue', $event)"
    @enter="$emit('enter')"
  />
</template>

<script setup lang="ts">
import { Search } from 'lucide-vue-next'
import AppInput from './AppInput.vue'

withDefaults(defineProps<{ modelValue?: string; placeholder?: string }>(), {
  modelValue: '',
  placeholder: '搜索...'
})

defineEmits<{ 'update:modelValue': [string]; enter: [] }>()
</script>
