/**
 * renderMarkdown 是 XSS 的最后防线：LLM 输出经它渲染后 v-html 注入 DOM。
 * 这些断言守住净化行为不回归。
 */
import { describe, it, expect } from 'vitest'
import { renderMarkdown } from '../markdown'

describe('renderMarkdown XSS 防护', () => {
  it('剥离 script 注入', () => {
    const html = renderMarkdown('hello <script>alert(1)</script> world')
    expect(html).not.toContain('<script')
    expect(html).toContain('hello')
  })

  it('内联 HTML 不产生真实标签（html:false 转义为文本）', () => {
    const html = renderMarkdown('<img src=x onerror=alert(1)>')
    // 原文以转义文本保留是安全的；关键是不能出现真实的 <img> 标签
    expect(html).not.toContain('<img')
    expect(html).toContain('&lt;img')
  })

  it('javascript: 协议不产生可点击链接', () => {
    const html = renderMarkdown('[click](javascript:alert(1))')
    // markdown-it 拒绝危险协议，整体退化为纯文本；不能出现 <a> 或 href
    expect(html).not.toContain('<a')
    expect(html).not.toContain('href')
  })

  it('iframe 等危险标签被移除', () => {
    const html = renderMarkdown('<iframe src="https://evil.example"></iframe>')
    expect(html).not.toContain('<iframe')
  })
})

describe('renderMarkdown 渲染行为', () => {
  it('基础 Markdown 正常渲染', () => {
    const html = renderMarkdown('# 标题\n\n**加粗**')
    expect(html).toContain('<h1>')
    expect(html).toContain('<strong>加粗</strong>')
  })

  it('代码块带工具栏结构，正文被 HTML 转义', () => {
    const html = renderMarkdown('```js\nconst a = "<b>"\n```')
    expect(html).toContain('class="code-block"')
    expect(html).toContain('data-lang="js"')
    expect(html).toContain('data-copy')
    // 代码内容中的 <b> 必须被转义而不是成为真实标签
    expect(html).toContain('&lt;b&gt;')
  })

  it('代码块语言名本身被转义，无法借 info 字符串注入属性', () => {
    const html = renderMarkdown('```"><img src=x onerror=alert(1)>\ncode\n```')
    expect(html).not.toContain('onerror')
  })

  it('流式光标在净化后注入且内联跟随内容', () => {
    const caret = '<span class="caret"></span>'
    const html = renderMarkdown('正在输出', caret)
    expect(html).toContain(caret)
    // 光标应在段落内部（内联），而非追加在末尾另起一行
    expect(html).toMatch(/正在输出[\s\S]*<span class="caret">[\s\S]*<\/p>/)
  })

  it('输出不含哨兵字符残留', () => {
    // 哨兵为私有区字符（见 utils/markdown.ts CARET_SENTINEL），任何私有区字符都不应漏出
    const priv = /[-]/
    expect(priv.test(renderMarkdown('普通内容'))).toBe(false)
    expect(priv.test(renderMarkdown('正在输出', '<span class="caret"></span>'))).toBe(false)
  })
})
