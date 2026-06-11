/**
 * 流式 Markdown 节流渲染。
 *
 * 流式输出时 token 往往一帧内到达多个，若每个 token 都触发 Markdown 解析 + 渲染会阻塞主线程。
 * 这里用 requestAnimationFrame 把视图快照（display）的更新合并到每帧至多一次：
 * - text：完整原始文本，流结束后用于提交到消息列表
 * - display：节流后的快照，驱动视图渲染
 * - append：累加 token；flush：强制立即同步（流结束时调用）；reset：清空
 *
 * 可选 onUpdate 回调在每次节流刷新时触发，供调用方把快照写入自有数据结构
 * （如 RAG 页按轮次保存答案）；仅用 display ref 的调用方（如 Chat 页）可忽略它。
 */
import { ref } from 'vue'

export function useStreamingMarkdown(onUpdate?: (text: string) => void) {
  const text = ref('')
  const display = ref('')
  let raf = 0

  function emit() {
    display.value = text.value
    onUpdate?.(text.value)
  }

  function append(chunk: string) {
    text.value += chunk
    if (raf) return
    raf = requestAnimationFrame(() => {
      raf = 0
      emit()
    })
  }

  function flush() {
    if (raf) {
      cancelAnimationFrame(raf)
      raf = 0
    }
    emit()
  }

  function reset() {
    if (raf) {
      cancelAnimationFrame(raf)
      raf = 0
    }
    text.value = ''
    display.value = ''
  }

  return { text, display, append, flush, reset }
}
