/**
 * 对话/问答页通用的「智能自动滚动」逻辑。
 *
 * 设计目标（性能优先 + 不打断用户阅读历史）：
 * - pinned：用户是否「锚定」在底部。只有锚定时才跟随新内容自动滚动。
 * - 用户向上滚动查看历史 → pinned 置 false → 停止自动跟随。
 * - 用户重新滚回底部（进入 threshold 容差区）→ pinned 置 true → 恢复跟随。
 * - scheduleScroll：流式 token 高频到达时用 requestAnimationFrame 合并滚动写入，
 *   每帧至多触发一次 scrollTop 赋值，避免频繁布局抖动；非锚定时直接跳过，零开销。
 * - onScroll 监听器自身也用 rAF 节流读取，避免滚动事件高频触发 reflow。
 *
 * 不使用 scrollIntoView：直接写 scrollTop = scrollHeight，避免其隐式平滑行为与
 * 对每条消息节点的重复测量。
 */
import { ref, nextTick, onBeforeUnmount } from 'vue'

export function useChatScroll(threshold = 80) {
  const containerRef = ref<HTMLElement>()
  /** 用户是否锚定在底部（决定是否自动跟随） */
  const pinned = ref(true)
  let writeRaf = 0
  let readRaf = 0

  /** 是否处于底部容差区内（distance ≤ threshold 视为「在底部」） */
  function isAtBottom(el: HTMLElement) {
    return el.scrollHeight - el.scrollTop - el.clientHeight <= threshold
  }

  /** 滚动事件回调：用 rAF 节流读取，更新 pinned 状态 */
  function onScroll() {
    if (readRaf) return
    readRaf = requestAnimationFrame(() => {
      readRaf = 0
      const el = containerRef.value
      if (el) pinned.value = isAtBottom(el)
    })
  }

  /**
   * 立即滚到底部（加载历史、切换会话、用户点击「回到底部」时用）。
   * force=true 时强制重新锚定（无视当前 pinned），用于明确的「回到底部」意图。
   */
  async function scrollToBottom(force = true) {
    await nextTick()
    const el = containerRef.value
    if (!el) return
    if (force) pinned.value = true
    el.scrollTop = el.scrollHeight
  }

  /**
   * 流式期间的节流滚动：仅在锚定时生效。
   * 非锚定（用户正在看历史）直接返回，绝不打断用户。
   */
  function scheduleScroll() {
    if (!pinned.value || writeRaf) return
    writeRaf = requestAnimationFrame(() => {
      writeRaf = 0
      const el = containerRef.value
      // 二次校验 pinned：rAF 回调执行前用户可能已上滚
      if (el && pinned.value) el.scrollTop = el.scrollHeight
    })
  }

  onBeforeUnmount(() => {
    if (writeRaf) cancelAnimationFrame(writeRaf)
    if (readRaf) cancelAnimationFrame(readRaf)
  })

  return { containerRef, pinned, scrollToBottom, scheduleScroll, onScroll }
}