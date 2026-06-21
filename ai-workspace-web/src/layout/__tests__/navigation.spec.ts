import { describe, expect, it } from 'vitest'
import { getVisibleNavGroups, isNavItemActive } from '../navigation'

describe('layout navigation', () => {
  it('hides admin-only groups for regular users', () => {
    const groups = getVisibleNavGroups(false)

    expect(groups.some((group) => group.label === '系统')).toBe(false)
    expect(groups.flatMap((group) => group.items).map((item) => item.path)).not.toContain('/system/user')
  })

  it('shows admin-only groups for administrators', () => {
    const groups = getVisibleNavGroups(true)

    expect(groups.some((group) => group.label === '系统')).toBe(true)
    expect(groups.flatMap((group) => group.items).map((item) => item.path)).toContain('/system/user')
  })

  it('marks exact and nested routes as active', () => {
    expect(isNavItemActive('/knowledge/base', '/knowledge/base')).toBe(true)
    expect(isNavItemActive('/knowledge/base/detail', '/knowledge/base')).toBe(true)
    expect(isNavItemActive('/knowledge/rag', '/knowledge/base')).toBe(false)
  })
})
