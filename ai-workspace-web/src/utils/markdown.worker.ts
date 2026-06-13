/**
 * Markdown 解析 Web Worker。
 *
 * 把 markdown-it 的解析工作（整条渲染链里最重的 CPU 部分）移出主线程，避免流式
 * 输出大段内容 / 含大量代码块时同步解析阻塞主线程导致页面滚动掉帧。
 *
 * 协议：主线程 postMessage({ id, content, withCaret }) → worker 回 { id, html }，
 * html 为「未净化」的 raw HTML（净化依赖 DOM，仍由主线程的 sanitizeRendered 完成）。
 * id 用于让主线程把异步回包与最新请求对齐，丢弃过期结果。
 */
import { renderRaw } from './markdown'

interface RenderRequest {
  id: number
  content: string
  withCaret: boolean
}

self.onmessage = (e: MessageEvent<RenderRequest>) => {
  const { id, content, withCaret } = e.data
  const html = renderRaw(content, withCaret)
  ;(self as unknown as Worker).postMessage({ id, html })
}
