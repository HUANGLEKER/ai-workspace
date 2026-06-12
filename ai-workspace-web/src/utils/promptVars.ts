/**
 * 提示词 {{变量}} 占位符解析与填充（P1-5）
 * 供 PromptPicker 使用；独立成模块以便单测。
 */

/** 提取 {{变量}} 占位符名（去重、保序，容忍内部空白：{{ 主题 }}） */
export function extractVars(content: string): string[] {
  const vars: string[] = []
  for (const m of content.matchAll(/\{\{\s*([^{}]+?)\s*\}\}/g)) {
    if (!vars.includes(m[1])) vars.push(m[1])
  }
  return vars
}

/** 用已填值替换占位符；未填的保留原样便于用户继续编辑 */
export function fillVars(content: string, values: Record<string, string>): string {
  return content.replace(/\{\{\s*([^{}]+?)\s*\}\}/g, (raw, name: string) => {
    const v = values[name]?.trim()
    return v ? v : raw
  })
}
