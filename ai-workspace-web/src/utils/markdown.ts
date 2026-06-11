/**
 * Markdown 渲染工具
 *
 * 统一供 Chat / RAG 等流式输出页复用，避免各页重复创建 MarkdownIt 实例与渲染逻辑。
 * 关键安全约束：LLM 返回内容会经 v-html 注入 DOM，必须用 DOMPurify 清洗以防 XSS。
 * 即便 MarkdownIt 已设 html:false，仍保留清洗作为纵深防御（如 linkify 产生的危险协议链接）。
 */
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'

const md = new MarkdownIt({ html: false, linkify: true, typographer: true })

/** 渲染 Markdown 字符串为经过净化的安全 HTML */
export function renderMarkdown(content: string): string {
  return DOMPurify.sanitize(md.render(content || ''))
}