<template>
  <div class="flex items-start gap-3">
    <AppAvatar :icon="Bot" variant="dark" />
    <div class="flex flex-col gap-2 rounded-2xl bg-zinc-100/50 px-4 py-3 dark:bg-zinc-800">
      <!-- 标题：进行中时带轻微脉冲，全部完成后变为静态 -->
      <div class="flex items-center gap-2 text-sm font-medium text-zinc-700 dark:text-zinc-200">
        <Loader2 v-if="inProgress" class="h-3.5 w-3.5 animate-spin text-zinc-400" />
        <Sparkles v-else class="h-3.5 w-3.5 text-zinc-400" />
        <span :class="inProgress ? 'thinking-shimmer' : ''">Thinking...</span>
      </div>
      <!-- 阶段清单：逐项点亮，✓ / 转圈 / 待办三态 -->
      <ul class="flex flex-col gap-1.5 pl-0.5">
        <li
          v-for="phase in phases"
          :key="phase.key"
          class="flex items-center gap-2 text-xs transition-colors duration-200"
          :class="phaseTextClass(phase.status)"
        >
          <Check v-if="phase.status === 'done'" class="h-3.5 w-3.5 shrink-0 text-emerald-500" />
          <Loader2 v-else-if="phase.status === 'active'" class="h-3.5 w-3.5 shrink-0 animate-spin text-zinc-400" />
          <Circle v-else class="h-3 w-3 shrink-0 text-zinc-300 dark:text-zinc-600" />
          <span>{{ phase.label }}</span>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * Feature 1 的展示组件：Thinking... + 三阶段清单。
 * 纯展示，状态由父级的 useThinkingPhases 驱动（见 phases / inProgress props）。
 */
import { Bot, Check, Circle, Loader2, Sparkles } from 'lucide-vue-next'
import { AppAvatar } from '@/components/ui'
import type { ThinkingPhase, PhaseStatus } from '@/composables/useThinkingPhases'

defineProps<{ phases: ThinkingPhase[]; inProgress: boolean }>()

function phaseTextClass(status: PhaseStatus) {
  if (status === 'done') return 'text-zinc-600 dark:text-zinc-300'
  if (status === 'active') return 'text-zinc-700 dark:text-zinc-200'
  return 'text-zinc-400 dark:text-zinc-500'
}
</script>
