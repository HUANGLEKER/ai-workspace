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

/**
 * 私有区占位符：流式渲染时先把它拼到原文末尾参与 Markdown 解析，
 * 这样它会落在「最后一个文本节点」内部（段落/列表项/代码块末尾），
 * 渲染后再替换为光标 HTML —— 光标便能内联跟随内容末尾，而非另起一行。
 */
const CARET_SENTINEL = '' //''

/**
 * 渲染 Markdown 字符串为经过净化的安全 HTML。
 *
 * @param content  原始 Markdown 文本
 * @param caretHtml 可选的光标 HTML 片段；流式输出时传入，会被内联拼接到内容末尾。
 *                  注意：光标 HTML 在 DOMPurify 之后注入，属可信片段，不参与净化。
 */
export function renderMarkdown(content: string, caretHtml = ''): string {
  const source = content || ''
  if (!caretHtml) return DOMPurify.sanitize(md.render(source))
  const html = DOMPurify.sanitize(md.render(source + CARET_SENTINEL))
  return html.replace(CARET_SENTINEL, caretHtml)
}