/**
 * vue-virtual-scroller 2.0.0-beta 未随包发布类型声明，这里补一份最小可用的组件 shim，
 * 仅声明 Chat 虚拟滚动用到的 DynamicScroller / DynamicScrollerItem 与其暴露的方法。
 */
declare module 'vue-virtual-scroller' {
  import type { DefineComponent } from 'vue'

  export interface DynamicScrollerInstance {
    /** 滚动到底部（保持自动跟随时调用） */
    scrollToBottom(): void
    /** 滚动到指定 item（按 index） */
    scrollToItem(index: number): void
  }

  export const DynamicScroller: DefineComponent<{
    items: unknown[]
    minItemSize: number
    keyField?: string
    direction?: 'vertical' | 'horizontal'
    buffer?: number
  }> & { new (): { scrollToBottom(): void; scrollToItem(i: number): void } }

  export const DynamicScrollerItem: DefineComponent<{
    item: unknown
    active: boolean
    sizeDependencies?: unknown[]
    dataIndex: number
  }>

  export const RecycleScroller: DefineComponent<Record<string, unknown>>
}
