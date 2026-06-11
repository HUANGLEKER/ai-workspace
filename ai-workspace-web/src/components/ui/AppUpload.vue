<template>
  <div>
    <input
      ref="inputRef"
      type="file"
      class="hidden"
      :accept="accept"
      :multiple="multiple"
      @change="onPick"
    />
    <span @click="inputRef?.click()">
      <slot>
        <AppButton variant="primary" :icon="Upload" :loading="uploading">上传文件</AppButton>
      </slot>
    </span>
  </div>
</template>

<script setup lang="ts">
/**
 * 文件上传组件
 *
 * 通过原生 fetch 携带 Authorization 头直传后端，
 * beforeUpload 返回 false 可拦截单个文件；每个文件独立上报 success/error。
 */
import { ref } from 'vue'
import { Upload } from 'lucide-vue-next'
import AppButton from './AppButton.vue'

const props = defineProps<{
  action: string
  data?: Record<string, string | number>
  accept?: string
  multiple?: boolean
  beforeUpload?: (file: File) => boolean
}>()

const emit = defineEmits<{ success: [File]; error: [File] }>()

const inputRef = ref<HTMLInputElement>()
const uploading = ref(false)

async function onPick(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!files.length) return

  uploading.value = true
  try {
    for (const file of files) {
      if (props.beforeUpload && !props.beforeUpload(file)) continue
      const form = new FormData()
      form.append('file', file)
      for (const [k, v] of Object.entries(props.data || {})) form.append(k, String(v))
      try {
        const res = await fetch(props.action, {
          method: 'POST',
          headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` },
          body: form
        })
        if (!res.ok) throw new Error(String(res.status))
        emit('success', file)
      } catch {
        emit('error', file)
      }
    }
  } finally {
    uploading.value = false
  }
}
</script>
