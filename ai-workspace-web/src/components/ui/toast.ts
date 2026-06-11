/**
 * 全局 Toast 状态
 *
 * 调用 toast.success/error/warning/info 推入消息，
 * AppToaster.vue 渲染队列并在超时后自动移除。
 */
import { reactive } from 'vue'

export type ToastType = 'success' | 'error' | 'warning' | 'info'

export interface ToastItem {
  id: number
  type: ToastType
  message: string
}

export const toastState = reactive<{ items: ToastItem[] }>({ items: [] })

let seq = 0

function push(type: ToastType, message: string, duration = 3000) {
  const id = ++seq
  toastState.items.push({ id, type, message })
  setTimeout(() => {
    const idx = toastState.items.findIndex((t) => t.id === id)
    if (idx >= 0) toastState.items.splice(idx, 1)
  }, duration)
}

export const toast = {
  success: (m: string) => push('success', m),
  error: (m: string) => push('error', m, 4000),
  warning: (m: string) => push('warning', m),
  info: (m: string) => push('info', m)
}
