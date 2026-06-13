/**
 * 异步 Markdown 渲染（Web Worker 离线程解析 + 主线程净化）。
 *
 * 把 markdown-it 解析下放到共享 Worker（见 utils/markdown.worker.ts），主线程仅做
 * DOMPurify 净化与光标注入，从而消除流式输出大段内容时主线程的解析卡顿。
 *
 * 设计：
 * - 单例 Worker：全应用共享一个，按自增 id 把回包与最新请求对齐，过期回包丢弃。
 * - 优雅降级：环境不支持 Worker（如测试 / 老浏览器）时回退到同步 renderMarkdown，
 *   行为与改造前一致。
 * - 返回 html ref 与 render(content, caretHtml) 触发器；调用方在 watch 中调用。
 */
import { ref, onUnmounted } from 'vue'
import { renderMarkdown, sanitizeRendered } from '@/utils/markdown'

interface WorkerReply {
  id: number
  html: string
}

// 全应用共享单例 Worker，避免每个消息组件各起一个线程。
let sharedWorker: Worker | null = null
let workerUnavailable = false

function getWorker(): Worker | null {
  if (workerUnavailable) return null
  if (sharedWorker) return sharedWorker
  try {
    sharedWorker = new Worker(new URL('@/utils/markdown.worker.ts', import.meta.url), {
      type: 'module',
    })
    return sharedWorker
  } catch {
    // SSR / 测试 / 不支持 module worker 的环境：永久回退同步路径。
    workerUnavailable = true
    return null
  }
}

let seq = 0

export function useAsyncMarkdown() {
  const html = ref('')
  // 本 composable 实例的「最新请求 id」，回包晚于它则丢弃（避免乱序覆盖）。
  let latest = 0
  // 待净化注入的光标片段，随每次 render 调用更新；回包到达时与之拼合。
  let pendingCaret = ''

  const worker = getWorker()

  function onMessage(e: MessageEvent<WorkerReply>) {
    if (e.data.id !== latest) return
    html.value = sanitizeRendered(e.data.html, pendingCaret)
  }

  if (worker) worker.addEventListener('message', onMessage)

  function render(content: string, caretHtml = '') {
    if (!worker) {
      // 同步回退：直接主线程渲染。
      html.value = renderMarkdown(content, caretHtml)
      return
    }
    pendingCaret = caretHtml
    latest = ++seq
    worker.postMessage({ id: latest, content, withCaret: !!caretHtml })
  }

  onUnmounted(() => {
    // 共享 Worker 不在此终止（其他组件仍在用），仅摘除本实例监听，置 latest 失效。
    latest = -1
    worker?.removeEventListener('message', onMessage)
  })

  return { html, render }
}
