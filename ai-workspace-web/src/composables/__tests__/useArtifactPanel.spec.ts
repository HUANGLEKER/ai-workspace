/**
 * Artifact 版本链（P3-4）：newTurn 归档版本、同内容不重复记、clearVersions 清空。
 */
import { describe, it, expect } from 'vitest'
import { useArtifactPanel } from '../useArtifactPanel'

// 用 HTML 围栏构造一个稳定 id 的 Artifact（html-1）
function htmlDoc(body: string): string {
  return '```html\n<div>' + body + '</div>\n```'
}

describe('useArtifactPanel 版本链', () => {
  it('每轮 newTurn 把上一轮成品归档为版本', () => {
    const p = useArtifactPanel()
    p.sync(htmlDoc('v1'))
    expect(p.artifacts.value[0].id).toBe('html-1')

    p.newTurn() // 归档 v1
    p.sync(htmlDoc('v2'))
    p.newTurn() // 归档 v2

    const chain = p.versions.value['html-1']
    expect(chain.map((a) => a.content)).toEqual(['<div>v1</div>', '<div>v2</div>'])
  })

  it('内容无变化不产生重复版本', () => {
    const p = useArtifactPanel()
    p.sync(htmlDoc('same'))
    p.newTurn()
    p.newTurn() // 内容未变，不应再记一版
    expect(p.versions.value['html-1'].length).toBe(1)
  })

  it('clearVersions 清空版本链（切换会话）', () => {
    const p = useArtifactPanel()
    p.sync(htmlDoc('x'))
    p.newTurn()
    expect(p.versions.value['html-1'].length).toBe(1)
    p.clearVersions()
    expect(Object.keys(p.versions.value).length).toBe(0)
  })

  it('归档为快照，与后续实时更新解耦', () => {
    const p = useArtifactPanel()
    p.sync(htmlDoc('snap'))
    p.newTurn()
    p.sync(htmlDoc('changed')) // 当前实时版本改变
    // 已归档的历史版本不受影响
    expect(p.versions.value['html-1'][0].content).toBe('<div>snap</div>')
  })
})
