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
 * 自定义围栏（```code```）渲染：为每个代码块加「工具栏」——语言标签 + 复制按钮。
 *
 * 结构：
 *   <div class="code-block" data-lang="java">
 *     <div class="code-block__toolbar">
 *       <span class="code-block__lang">java</span>
 *       <button data-copy>Copy</button>
 *     </div>
 *     <pre><code class="language-java">...</code></pre>
 *   </div>
 *
 * 复制逻辑不在此处实现：渲染产物经 v-html 注入，复制按钮的点击由 MarkdownView
 * 通过事件委托统一处理（读取兄弟 <code> 的 textContent），避免把代码正文重复
 * 编码进 data 属性。正文用 md.utils.escapeHtml 转义，防注入；<button>/data-* 均在
 * DOMPurify 默认白名单内，净化后保留。
 */
md.renderer.rules.fence = (tokens, idx) => {
  const token = tokens[idx]
  const info = (token.info || '').trim()
  const lang = md.utils.escapeHtml(info.split(/\s+/)[0] || 'text')
  const code = md.utils.escapeHtml(token.content)
  return (
    `<div class="code-block" data-lang="${lang}">` +
    `<div class="code-block__toolbar">` +
    `<span class="code-block__lang">${lang}</span>` +
    `<button type="button" class="code-block__copy" data-copy aria-label="复制代码">` +
    `<span class="code-block__copy-text">Copy</span>` +
    `</button>` +
    `</div>` +
    `<pre><code class="language-${lang}">${code}</code></pre>` +
    `</div>`
  )
}

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
  return sanitizeRendered(renderRaw(content, !!caretHtml), caretHtml)
}

/**
 * 纯 markdown-it 渲染：把 Markdown 文本解析为「未净化」的 HTML。
 *
 * 这一步是整条渲染链里最重的 CPU 工作（解析 + linkify + typographer + fence 规则），
 * 因此被单独抽出，既供主线程同步 fallback 复用，也供 Web Worker 离线程调用
 * （见 markdown.worker.ts），把流式输出大段内容时的解析开销移出主线程。
 * withCaret 为 true 时把光标哨兵拼到原文末尾参与解析，使其落入最后一个文本节点内部。
 */
export function renderRaw(content: string, withCaret = false): string {
  return md.render((content || '') + (withCaret ? CARET_SENTINEL : ''))
}

/**
 * 净化已渲染的 HTML 并注入光标。
 *
 * 必须在主线程执行（DOMPurify 依赖 DOM）。光标 HTML 在净化之后注入，属可信片段。
 */
export function sanitizeRendered(rawHtml: string, caretHtml = ''): string {
  const clean = DOMPurify.sanitize(rawHtml)
  return caretHtml ? clean.replace(CARET_SENTINEL, caretHtml) : clean
}