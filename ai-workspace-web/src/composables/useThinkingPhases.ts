/**
 * Feature 1：Thinking 状态展示
 *
 * AI 响应开始后，先展示一段「思考过程」清单：
 *   Thinking...
 *     ✓ Analyzing Request
 *     ✓ Retrieving Context
 *     ✓ Generating Response
 *
 * 设计要点：
 * - 不暴露任何内部 Prompt：阶段名是固定的通用标签，与真实推理内容无关，
 *   仅由「流的生命周期信号」驱动状态机（开始 → 计时推进 → 首 token 到达 → 完成）。
 * - 支持流式更新：start() 后前两个阶段按 PHASE_DELAY 自动点亮，第三阶段在
 *   首个正文 token 到达（markGenerating）时点亮，与真实流式渲染对齐。
 * - 纯前端、零网络：不需要后端额外协议，任何 SSE chat 端点都可直接复用。
 */
import { ref, computed, onBeforeUnmount } from 'vue'

export type PhaseStatus = 'pending' | 'active' | 'done'

export interface ThinkingPhase {
  key: string
  label: string
  status: PhaseStatus
}

/** 前两阶段的自动推进间隔（ms）。第三阶段由首 token 事件驱动，不计时。 */
const PHASE_DELAY = 450

export function useThinkingPhases() {
  const phases = ref<ThinkingPhase[]>([])
  /** 是否处于「思考中」展示态（首 token 到达后仍保留，直到 finish 收起） */
  const thinking = ref(false)
  const timers: ReturnType<typeof setTimeout>[] = []

  function clearTimers() {
    timers.forEach(clearTimeout)
    timers.length = 0
  }

  function set(idx: number, status: PhaseStatus) {
    const p = phases.value[idx]
    if (p) p.status = status
  }

  /** 启动思考序列：初始化三阶段，自动点亮前两阶段 */
  function start() {
    clearTimers()
    thinking.value = true
    phases.value = [
      { key: 'analyze', label: 'Analyzing Request', status: 'active' },
      { key: 'retrieve', label: 'Retrieving Context', status: 'pending' },
      { key: 'generate', label: 'Generating Response', status: 'pending' }
    ]
    timers.push(
      setTimeout(() => {
        set(0, 'done')
        set(1, 'active')
      }, PHASE_DELAY)
    )
    timers.push(
      setTimeout(() => {
        set(1, 'done')
        set(2, 'active')
      }, PHASE_DELAY * 2)
    )
  }

  /**
   * 插入/更新一个由外部事件驱动的动态阶段（如「联网搜索中」），点亮为 active。
   * 已存在同 key 则原地激活，避免重复插入；置于「Generating」之前以符合时序直觉。
   */
  function addPhase(key: string, label: string) {
    const existing = phases.value.find((p) => p.key === key)
    if (existing) {
      existing.status = 'active'
      return
    }
    const genIdx = phases.value.findIndex((p) => p.key === 'generate')
    const phase: ThinkingPhase = { key, label, status: 'active' }
    if (genIdx >= 0) phases.value.splice(genIdx, 0, phase)
    else phases.value.push(phase)
  }

  /** 首个正文 token 到达：除「Generating」外的所有阶段标记完成，点亮 Generating（兼容动态插入的阶段） */
  function markGenerating() {
    clearTimers()
    phases.value.forEach((p) => {
      p.status = p.key === 'generate' ? 'active' : 'done'
    })
  }

  /** 流结束：标记全部完成（调用方通常随后将整块收起，让位给最终消息） */
  function finish() {
    clearTimers()
    phases.value.forEach((_, i) => set(i, 'done'))
  }

  /** 完全复位（切换会话、出错、卸载时调用） */
  function reset() {
    clearTimers()
    thinking.value = false
    phases.value = []
  }

  /** 是否仍有阶段在进行中（用于决定 Thinking... 标题是否带动效） */
  const inProgress = computed(() => phases.value.some((p) => p.status !== 'done'))

  onBeforeUnmount(clearTimers)

  return { phases, thinking, inProgress, start, addPhase, markGenerating, finish, reset }
}
