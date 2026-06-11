/**
 * 全局确认弹窗（Promise 风格 confirm / alert）
 *
 * confirm(options) 返回 Promise<boolean>：确认为 true，取消/关闭为 false。
 * alertBox(options) 仅展示确认按钮。
 * AppConfirm.vue 为渲染单例，挂载于 App.vue。
 */
import { reactive } from 'vue'

interface ConfirmOptions {
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  danger?: boolean
}

interface ConfirmState extends Required<ConfirmOptions> {
  visible: boolean
  alertOnly: boolean
  resolve: ((v: boolean) => void) | null
}

export const confirmState = reactive<ConfirmState>({
  visible: false,
  alertOnly: false,
  title: '确认',
  message: '',
  confirmText: '确定',
  cancelText: '取消',
  danger: false,
  resolve: null
})

export function confirm(options: ConfirmOptions): Promise<boolean> {
  return new Promise((resolve) => {
    confirmState.title = options.title ?? '确认'
    confirmState.message = options.message
    confirmState.confirmText = options.confirmText ?? '确定'
    confirmState.cancelText = options.cancelText ?? '取消'
    confirmState.danger = options.danger ?? false
    confirmState.alertOnly = false
    confirmState.resolve = resolve
    confirmState.visible = true
  })
}

export function alertBox(options: ConfirmOptions): Promise<boolean> {
  return new Promise((resolve) => {
    confirmState.title = options.title ?? '提示'
    confirmState.message = options.message
    confirmState.confirmText = options.confirmText ?? '知道了'
    confirmState.cancelText = ''
    confirmState.danger = false
    confirmState.alertOnly = true
    confirmState.resolve = resolve
    confirmState.visible = true
  })
}

export function settle(value: boolean) {
  confirmState.visible = false
  confirmState.resolve?.(value)
  confirmState.resolve = null
}
