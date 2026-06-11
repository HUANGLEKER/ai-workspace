/**
 * 对话/问答页通用的自动触底滚动逻辑。
 *
 * 将 Chat 与 RAG 页重复的滚动控制收敛于此：
 * - scrollToBottom：等待 DOM 更新后立即滚到底部（加载历史、切换会话时用）
 * - scheduleScroll：流式 token 高频到达时用 requestAnimationFrame 合并滚动请求，避免布局抖动
 */
import { ref, nextTick, onBeforeUnmount } from 'vue'

export function useChatScroll() {
  const containerRef = ref<HTMLElement>()
  let scrollRaf = 0

  async function scrollToBottom() {
    await nextTick()
    const el = containerRef.value
    if (el) el.scrollTop = el.scrollHeight
  }

  function scheduleScroll() {
    if (scrollRaf) return
    scrollRaf = requestAnimationFrame(() => {
      scrollRaf = 0
      const el = containerRef.value
      if (el) el.scrollTop = el.scrollHeight
    })
  }

  onBeforeUnmount(() => {
    if (scrollRaf) cancelAnimationFrame(scrollRaf)
  })

  return { containerRef, scrollToBottom, scheduleScroll }
}
