<template>
  <!--
    对话/问答页通用外壳：左侧可折叠会话侧栏 + 右侧主区域。
    Chat 与 RAG 共用，消除两页重复的两栏骨架与折叠逻辑。
    - sidebar-header 插槽：新建按钮（RAG 额外放知识库选择器）
    - sidebar-list  插槽：会话列表（通常用 <ConversationList>）
    - 默认插槽：主区域（空态 / 标题栏 / 消息流）
    折叠态由本组件内部持有，并通过插槽作用域 `collapsed` 暴露给上述两个具名插槽。
  -->
  <div class="flex flex-1 overflow-hidden bg-surface">
    <!-- 会话列表侧边栏 -->
    <div
      class="relative flex shrink-0 flex-col border-r border-line bg-canvas transition-all duration-200 ease-out"
      :class="collapsed ? 'w-[72px]' : 'w-[260px]'"
    >
      <div class="flex flex-col gap-2 border-b border-line p-3">
        <slot name="sidebar-header" :collapsed="collapsed" />
      </div>

      <div class="relative flex-1 overflow-y-auto p-2">
        <slot name="sidebar-list" :collapsed="collapsed" />
      </div>

      <button
        class="flex h-11 shrink-0 items-center justify-center border-t border-line text-zinc-500 transition-all duration-200 ease-out hover:bg-zinc-100/50 hover:text-zinc-800 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100"
        @click="collapsed = !collapsed"
      >
        <PanelLeft class="h-4 w-4" />
      </button>
    </div>

    <!-- 主区域 -->
    <div class="flex flex-1 flex-col overflow-hidden">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { PanelLeft } from 'lucide-vue-next'

const collapsed = ref(false)
</script>
