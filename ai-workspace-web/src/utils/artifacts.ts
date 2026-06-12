/**
 * Artifact 识别工具
 *
 * 从一段（助手）消息正文中解析出可进入「Artifact 预览区」的结构化产物：
 * Markdown / SQL / HTML / JSON / Mermaid / PRD / 长代码。
 *
 * 设计原则：
 * - 纯函数、无副作用：流式期间可对「尚未完结」的局部文本反复调用（实时更新），
 *   因此对「未闭合围栏」也要尽量给出可用结果（取已到达的部分内容）。
 * - 不暴露任何内部 Prompt：仅依据可见正文做结构判定。
 * - 结果带稳定 id（基于类型 + 序号），供面板在流式刷新时按 id 原地更新而非整列重建。
 */

export type ArtifactType = 'markdown' | 'sql' | 'html' | 'json' | 'mermaid' | 'prd' | 'code'

export interface Artifact {
  /** 稳定标识：`${type}-${序号}`，流式刷新时据此原地更新 */
  id: string
  type: ArtifactType
  /** 面板标签页标题（如 "SQL"、"Mermaid 图"、"PRD 文档"） */
  title: string
  /** 代码高亮语言（markdown/prd 用 'markdown'，html 用 'html' 等） */
  language: string
  /** 产物正文（已去除围栏标记） */
  content: string
}

/** 长代码阈值：单个围栏代码块超过该行数即视为值得独立展示的 Artifact */
const LONG_CODE_LINES = 25
/** 长 Markdown 文档阈值：无围栏但正文超过该行数且含标题，整体作为文档型 Artifact */
const LONG_DOC_LINES = 40

const TYPE_TITLE: Record<ArtifactType, string> = {
  markdown: 'Markdown',
  sql: 'SQL',
  html: 'HTML',
  json: 'JSON',
  mermaid: 'Mermaid 图',
  prd: 'PRD 文档',
  code: '代码'
}

/** 把围栏 info 串（如 "sql"、"html"、"ts"）归一化为 ArtifactType；非独立产物类型返回 null */
function classifyFence(lang: string, content: string): ArtifactType | null {
  const l = lang.toLowerCase()
  if (l === 'mermaid') return 'mermaid'
  if (l === 'sql') return 'sql'
  if (l === 'html' || l === 'xml' || l === 'svg') return 'html'
  if (l === 'json' || l === 'json5') return 'json'
  // 其余语言仅当「长代码」时才独立成 Artifact，短代码片段留在气泡内即可
  if (content.split('\n').length >= LONG_CODE_LINES) return 'code'
  return null
}

/** PRD 启发式：含 PRD/产品需求字样，或标题数量较多的长文档 */
function looksLikePRD(content: string): boolean {
  if (/(^|\n)\s*#{1,3}\s.*(PRD|产品需求|需求文档|Product Requirement)/i.test(content)) return true
  const headings = (content.match(/(^|\n)#{1,6}\s/g) || []).length
  return headings >= 4 && content.split('\n').length >= LONG_DOC_LINES
}

/**
 * 从正文中提取全部 Artifact。
 *
 * @param content 助手消息正文（可为流式期间的局部文本）
 */
export function extractArtifacts(content: string): Artifact[] {
  const src = content || ''
  const artifacts: Artifact[] = []
  const counter: Partial<Record<ArtifactType, number>> = {}

  const push = (type: ArtifactType, language: string, body: string) => {
    const n = (counter[type] = (counter[type] || 0) + 1)
    artifacts.push({
      id: `${type}-${n}`,
      type,
      title: n > 1 ? `${TYPE_TITLE[type]} ${n}` : TYPE_TITLE[type],
      language,
      content: body.trim()
    })
  }

  // 逐个匹配围栏代码块；同时兼容「已闭合」与流式期间「未闭合」（取到字符串末尾）的块。
  const fenceRe = /```([^\n`]*)\n([\s\S]*?)(?:```|$)/g
  let m: RegExpExecArray | null
  let hadFence = false
  while ((m = fenceRe.exec(src)) !== null) {
    const lang = (m[1] || '').trim().split(/\s+/)[0] || ''
    const body = m[2] || ''
    if (!body.trim()) continue
    const type = classifyFence(lang, body)
    if (!type) continue
    hadFence = true
    const language = type === 'mermaid' ? 'mermaid' : type === 'code' ? lang || 'text' : type
    push(type, language, body)
  }

  // 无独立围栏产物时，长 Markdown / PRD 文档整体作为一个文档型 Artifact
  if (!hadFence) {
    if (looksLikePRD(src)) {
      push('prd', 'markdown', src)
    } else if (src.split('\n').length >= LONG_DOC_LINES && /(^|\n)#{1,6}\s/.test(src)) {
      push('markdown', 'markdown', src)
    }
  }

  return artifacts
}

/** 该消息是否包含任何可预览 Artifact（轻量判断，供消息气泡决定是否显示「打开」入口） */
export function hasArtifact(content: string): boolean {
  return extractArtifacts(content).length > 0
}

/** 各 Artifact 类型下载时的文件扩展名 */
export function artifactExt(a: Artifact): string {
  switch (a.type) {
    case 'sql':
      return 'sql'
    case 'html':
      return 'html'
    case 'json':
      return 'json'
    case 'mermaid':
      return 'mmd'
    case 'markdown':
    case 'prd':
      return 'md'
    default:
      return extByLang(a.language)
  }
}

function extByLang(lang: string): string {
  const map: Record<string, string> = {
    javascript: 'js',
    typescript: 'ts',
    python: 'py',
    go: 'go',
    java: 'java',
    rust: 'rs',
    bash: 'sh',
    shell: 'sh',
    yaml: 'yml',
    css: 'css'
  }
  return map[lang.toLowerCase()] || 'txt'
}
