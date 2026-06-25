<template>
  <AppDialog
    :model-value="modelValue"
    title="请完成下列验证后继续"
    width="360px"
    @update:model-value="onOpenChange"
  >
    <div class="flex flex-col gap-3">
      <!-- 拼图区：底图 + 可拖动的拼图块 -->
      <div
        ref="stageRef"
        class="relative mx-auto overflow-hidden rounded-xl border border-line bg-zinc-100 dark:bg-zinc-800"
        :style="stageStyle"
      >
        <AppLoading v-if="loading" overlay />
        <template v-if="data">
          <img
            :src="data.masterImage"
            alt="captcha"
            draggable="false"
            class="block select-none"
            @load="onMasterLoad"
          />
          <img
            v-if="imgW > 0"
            :src="data.tileImage"
            alt="tile"
            draggable="false"
            class="pointer-events-none absolute select-none"
            :class="resetting ? 'transition-all duration-300 ease-out' : ''"
            :style="tileStyle"
          />
          <!-- 成功 / 失败的轻提示遮罩 -->
          <div
            v-if="status === 'success'"
            class="absolute inset-0 flex items-center justify-center bg-emerald-500/15 text-sm font-medium text-emerald-600"
          >
            验证通过
          </div>
        </template>
      </div>

      <!-- 拖动条 -->
      <div
        ref="trackRef"
        class="relative h-11 select-none rounded-xl border border-line bg-zinc-50 text-center text-sm leading-[2.75rem] text-ink-muted dark:bg-zinc-900"
        :style="{ width: imgW > 0 ? imgW + 'px' : '100%' }"
      >
        <span>{{ trackHint }}</span>
        <button
          type="button"
          class="absolute left-0 top-0 flex h-11 w-11 cursor-grab items-center justify-center rounded-xl border border-line bg-surface text-ink shadow-sm transition-colors duration-150 active:cursor-grabbing"
          :class="[
            resetting ? 'transition-all duration-300 ease-out' : '',
            status === 'success' ? 'border-emerald-500 text-emerald-600' : '',
            status === 'fail' ? 'border-red-500 text-red-500' : '',
          ]"
          :style="{ transform: `translateX(${state.dragLeft}px)` }"
          :disabled="!canDrag"
          aria-label="拖动滑块完成验证"
          @pointerdown="onPointerDown"
        >
          <Check v-if="status === 'success'" class="h-5 w-5" />
          <ArrowRight v-else class="h-5 w-5" />
        </button>
      </div>

      <!-- 底部操作 -->
      <div class="flex items-center justify-between text-xs text-ink-muted">
        <button
          type="button"
          class="flex items-center gap-1 rounded-md px-1.5 py-1 transition-colors hover:text-ink"
          @click="refresh"
        >
          <RefreshCw class="h-3.5 w-3.5" />
          刷新
        </button>
        <span v-if="status === 'fail'" class="text-red-500">验证失败，请重试</span>
      </div>
    </div>
  </AppDialog>
</template>

<script setup lang="ts">
/**
 * 登录滑块拼图验证弹窗。
 *
 * 流程：打开即调用 /auth/captcha/slide/init 取拼图 → 用户拖动把拼图块对齐缺口 →
 * 松手调用 /auth/captcha/slide/verify 上报落点 X → 通过则 emit('success', captchaToken)。
 *
 * 坐标对齐后端 go-captcha（与 go-captcha-vue 的 ratio 映射一致）：
 *   reportX = tileX + dragLeft * ratio，ratio = (maxWidth - tileX + (HANDLE_W - tileWidth)) / maxWidth，
 *   maxWidth = 底图宽 - HANDLE_W。底图按原始像素 1:1 渲染，确保上报 X 与后端答案同坐标系。
 *
 * 注意：后端校验时用 GetDel 消费答案，无论成败答案都失效，故失败后必须重新 init。
 */
import { reactive, ref, computed, watch } from 'vue'
import { ArrowRight, RefreshCw, Check } from 'lucide-vue-next'
import AppDialog from './AppDialog.vue'
import AppLoading from './AppLoading.vue'
import { initSlideCaptcha, verifySlideCaptcha } from '@/api/auth'
import type { SlideCaptchaInit } from '@/types'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [boolean]
  success: [captchaToken: string]
}>()

const HANDLE_W = 44 // 拖动把手宽度（px），与样式 w-11 保持一致

const data = ref<SlideCaptchaInit | null>(null)
const loading = ref(false)
const imgW = ref(0)
const resetting = ref(false)
const status = ref<'idle' | 'verifying' | 'success' | 'fail'>('idle')
const stageRef = ref<HTMLElement>()
const trackRef = ref<HTMLElement>()

const state = reactive({ dragLeft: 0, thumbLeft: 0 })

const canDrag = computed(
  () => !!data.value && imgW.value > 0 && status.value !== 'verifying' && status.value !== 'success',
)

const maxWidth = computed(() => Math.max(imgW.value - HANDLE_W, 1))

// 把手行程 → 拼图块行程的缩放比，使把手拉到底时拼图块恰好抵达底图右缘（imgW - tileWidth）
const ratio = computed(() => {
  if (!data.value) return 1
  const ad = HANDLE_W - data.value.tileWidth
  return (maxWidth.value - data.value.tileX + ad) / maxWidth.value
})

const stageStyle = computed(() => (imgW.value > 0 ? { width: imgW.value + 'px' } : { width: '300px', height: '180px' }))

const tileStyle = computed(() => {
  if (!data.value) return {}
  return {
    left: state.thumbLeft + 'px',
    top: data.value.tileY + 'px',
    width: data.value.tileWidth + 'px',
  }
})

const trackHint = computed(() => {
  if (status.value === 'success') return '验证通过'
  return '按住左边按钮拖动完成上方拼图'
})

function onOpenChange(open: boolean) {
  emit('update:modelValue', open)
}

function onMasterLoad(e: Event) {
  const img = e.target as HTMLImageElement
  imgW.value = img.naturalWidth
}

async function loadCaptcha() {
  loading.value = true
  status.value = 'idle'
  resetReader()
  try {
    data.value = await initSlideCaptcha()
    imgW.value = 0 // 等新底图 load 重新测量
  } catch {
    data.value = null
  } finally {
    loading.value = false
  }
}

function resetReader() {
  state.dragLeft = 0
  state.thumbLeft = data.value?.tileX ?? 0
}

function refresh() {
  loadCaptcha()
}

// —— 指针拖动 ——
let startX = 0
let dragging = false

function onPointerDown(e: PointerEvent) {
  if (!canDrag.value) return
  dragging = true
  resetting.value = false
  status.value = 'idle'
  startX = e.clientX
  ;(e.target as HTMLElement).setPointerCapture?.(e.pointerId)
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}

function onPointerMove(e: PointerEvent) {
  if (!dragging || !data.value) return
  let left = e.clientX - startX
  if (left < 0) left = 0
  if (left > maxWidth.value) left = maxWidth.value
  state.dragLeft = left
  state.thumbLeft = data.value.tileX + left * ratio.value
}

async function onPointerUp() {
  if (!dragging) return
  dragging = false
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  if (!data.value) return

  const reportX = Math.round(data.value.tileX + state.dragLeft * ratio.value)
  status.value = 'verifying'
  try {
    const res = await verifySlideCaptcha({ captchaId: data.value.captchaId, x: reportX })
    if (res.success && res.captchaToken) {
      status.value = 'success'
      const token = res.captchaToken
      setTimeout(() => {
        emit('success', token)
        emit('update:modelValue', false)
      }, 400)
      return
    }
  } catch {
    /* 失败统一走下方分支 */
  }
  // 失败：抖动复位 + 重新 init（答案已被后端消费）
  status.value = 'fail'
  snapBack()
  setTimeout(loadCaptcha, 600)
}

function snapBack() {
  resetting.value = true
  resetReader()
  setTimeout(() => (resetting.value = false), 320)
}

// 弹窗打开时拉取拼图；关闭时复位
watch(
  () => props.modelValue,
  (open) => {
    if (open) loadCaptcha()
    else {
      data.value = null
      status.value = 'idle'
    }
  },
)
</script>
