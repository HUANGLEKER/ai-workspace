/**
 * Feature 2：Artifact 面板状态。
 *
 * 维护「当前在右侧面板展示的 Artifact 集合 + 选中项 + 开合/全屏」状态，并提供
 * 与流式渲染对齐的实时更新：sync() 用最新文本重算 Artifact，按 id 原地合并，
 * 既能在流式期间持续刷新预览，又不丢失用户已选中的标签页。
 */
import { ref, computed } from 'vue'
import { extractArtifacts, type Artifact } from '@/utils/artifacts'

export function useArtifactPanel() {
  const open = ref(false)
  const fullscreen = ref(false)
  const artifacts = ref<Artifact[]>([])
  const activeId = ref('')
  /** 是否由流式自动打开：用户手动关闭后，本轮不再自动弹出，避免反复打断 */
  const autoOpenedThisTurn = ref(false)
  /** 用户本轮主动关闭过面板的标记 */
  const dismissed = ref(false)

  const active = computed<Artifact | null>(
    () => artifacts.value.find((a) => a.id === activeId.value) || artifacts.value[0] || null
  )

  /** 显式打开（点击消息上的「在面板打开」入口） */
  function show(list: Artifact[], focusId?: string) {
    if (!list.length) return
    artifacts.value = list
    activeId.value = focusId || (artifacts.value.some((a) => a.id === activeId.value) ? activeId.value : list[0].id)
    open.value = true
    dismissed.value = false
  }

  /**
   * 流式期间实时同步：从最新正文重算 Artifact 并原地合并。
   * 首次检测到 Artifact 且用户未主动关闭时自动打开面板（每轮仅自动开一次）。
   */
  function sync(content: string) {
    const next = extractArtifacts(content)
    if (!next.length) return
    // 按 id 合并：保留引用稳定，仅更新内容，避免预览整块重挂载
    const merged = next.map((n) => {
      const prev = artifacts.value.find((a) => a.id === n.id)
      return prev ? Object.assign(prev, n) : n
    })
    artifacts.value = merged
    if (!activeId.value || !merged.some((a) => a.id === activeId.value)) {
      activeId.value = merged[0].id
    }
    if (!open.value && !dismissed.value && !autoOpenedThisTurn.value) {
      open.value = true
      autoOpenedThisTurn.value = true
    }
  }

  /** 新一轮回复开始：重置「本轮自动打开/关闭」标记（不强制关已开的面板） */
  function newTurn() {
    autoOpenedThisTurn.value = false
    dismissed.value = false
  }

  function close() {
    open.value = false
    fullscreen.value = false
    dismissed.value = true
  }

  function select(id: string) {
    activeId.value = id
  }

  return { open, fullscreen, artifacts, activeId, active, show, sync, newTurn, close, select }
}
