<template>
  <!--
    消息流 + Artifact 分屏（Chat / RAG 共用）：左侧消息列表（含「回到底部」浮钮、Token 用量条、
    输入区插槽），右侧 Artifact 预览（开启时，可拖拽分隔）。
    滚动方法经 defineExpose 暴露给父页流式管线（scrollToBottom / scheduleScroll）。
    输入区通过 #composer 插槽注入，保证 DOM 顺序在消息流之下。
  -->
  <SplitterGroup direction="horizontal" class="flex flex-1 overflow-hidden">
    <SplitterPanel :default-size="artifact.open.value ? 58 : 100" :min-size="34" class="flex flex-col overflow-hidden">
      <!-- 消息区：relative 容器承载滚动区 + 浮动「回到底部」按钮 -->
      <div class="relative flex flex-1 overflow-hidden">
        <ChatMessageList
          ref="listRef"
          :messages="messages"
          :busy="streaming || caretFading"
          :no-animate-idx="noAnimateIdx"
          :streaming="streaming"
          :caret-fading="caretFading"
          :thinking="thinking"
          :stream-display="streamDisplay"
          :phases="phases"
          :in-progress="inProgress"
          :live-sources="liveSources"
          @copy="emit('copy', $event)"
          @regenerate="emit('regenerate', $event)"
          @delete="emit('delete', $event)"
          @open-artifact="(a) => artifact.show(a)"
        />

        <Transition name="msg">
          <button
            v-if="listRef && !listRef.pinned"
            class="absolute bottom-4 left-1/2 flex h-9 w-9 -translate-x-1/2 items-center justify-center rounded-full border border-line bg-surface text-zinc-600 shadow-md transition-all duration-200 ease-out hover:text-zinc-900 dark:text-zinc-300 dark:hover:text-white"
            @click="listRef?.scrollToBottom()"
          >
            <ArrowDown class="h-4 w-4" />
          </button>
        </Transition>
      </div>

      <!-- Token 用量统计条 -->
      <div
        v-if="displayUsage"
        class="flex shrink-0 items-center gap-3 border-t border-line px-5 py-1.5 text-xs text-zinc-400 dark:text-zinc-500"
      >
        <span>Prompt: <span class="font-medium text-zinc-600 dark:text-zinc-300">{{ displayUsage.promptTokens }}</span></span>
        <span>Completion: <span class="font-medium text-zinc-600 dark:text-zinc-300">{{ usageEstimating ? '~' : '' }}{{ displayUsage.completionTokens }}</span></span>
        <span>Total: <span class="font-medium text-zinc-600 dark:text-zinc-300">{{ usageEstimating ? '~' : displayUsage.totalTokens }}</span></span>
      </div>

      <!-- 输入区域（由父页注入） -->
      <slot name="composer" />
    </SplitterPanel>

    <!-- Artifact 预览面板（开启时） -->
    <template v-if="artifact.open.value">
      <SplitterResizeHandle class="group relative w-px shrink-0 bg-zinc-200 transition-colors hover:bg-zinc-400 dark:bg-zinc-800 dark:hover:bg-zinc-600">
        <div class="absolute inset-y-0 -left-1.5 -right-1.5" />
      </SplitterResizeHandle>
      <SplitterPanel :default-size="42" :min-size="25" class="overflow-hidden border-l border-line">
        <ArtifactPanel
          :artifacts="artifact.artifacts.value"
          :active-id="artifact.activeId.value"
          :active="artifact.active.value"
          :fullscreen="artifact.fullscreen.value"
          :versions="artifact.versions.value"
          @select="artifact.select"
          @close="artifact.close"
          @toggle-fullscreen="artifact.fullscreen.value = !artifact.fullscreen.value"
        />
      </SplitterPanel>
    </template>
  </SplitterGroup>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { SplitterGroup, SplitterPanel, SplitterResizeHandle } from 'radix-vue'
import { ArrowDown } from 'lucide-vue-next'
import type { ChatMessage, RagSource, TokenUsage } from '@/types'
import ChatMessageList from './ChatMessageList.vue'
import ArtifactPanel from './ArtifactPanel.vue'
import type { useArtifactPanel } from '@/composables/useArtifactPanel'
import type { ThinkingPhase } from '@/composables/useThinkingPhases'

defineProps<{
  messages: ChatMessage[]
  streaming: boolean
  caretFading: boolean
  noAnimateIdx: number
  streamDisplay: string
  thinking: boolean
  phases: ThinkingPhase[]
  inProgress: boolean
  liveSources?: RagSource[]
  displayUsage: TokenUsage | null
  usageEstimating: boolean
  artifact: ReturnType<typeof useArtifactPanel>
}>()

const emit = defineEmits<{ copy: [ChatMessage]; regenerate: [number]; delete: [number] }>()

const listRef = ref<InstanceType<typeof ChatMessageList> | null>(null)

defineExpose({
  scrollToBottom: (force = true) => listRef.value?.scrollToBottom(force),
  scheduleScroll: () => listRef.value?.scheduleScroll()
})
</script>
