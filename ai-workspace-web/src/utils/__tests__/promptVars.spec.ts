import { describe, it, expect } from 'vitest'
import { extractVars, fillVars } from '../promptVars'

describe('extractVars', () => {
  it('提取占位符并去重保序', () => {
    expect(extractVars('把{{原文}}翻译成{{语言}}，注意{{原文}}的语气')).toEqual(['原文', '语言'])
  })

  it('容忍占位符内部空白', () => {
    expect(extractVars('{{ 主题 }}和{{风格}}')).toEqual(['主题', '风格'])
  })

  it('无占位符返回空数组', () => {
    expect(extractVars('纯文本提示词')).toEqual([])
  })

  it('不匹配空的或嵌套的花括号', () => {
    expect(extractVars('{{}} {单层} {{a}}')).toEqual(['a'])
  })
})

describe('fillVars', () => {
  it('替换已填变量', () => {
    expect(fillVars('把{{原文}}翻译成{{语言}}', { 原文: 'hello', 语言: '中文' })).toBe('把hello翻译成中文')
  })

  it('未填的变量保留原样（用户可继续编辑）', () => {
    expect(fillVars('把{{原文}}翻译成{{语言}}', { 原文: 'hi' })).toBe('把hi翻译成{{语言}}')
  })

  it('同名占位符全部替换', () => {
    expect(fillVars('{{x}}+{{x}}', { x: '1' })).toBe('1+1')
  })

  it('空白值视为未填', () => {
    expect(fillVars('{{a}}', { a: '  ' })).toBe('{{a}}')
  })
})
