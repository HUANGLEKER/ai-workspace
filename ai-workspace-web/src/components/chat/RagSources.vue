<template>
  <div v-if="sources.length" class="mt-1 w-full max-w-full border-t border-dashed border-zinc-200/80 pt-2.5 dark:border-zinc-700">
    <div class="mb-1.5 flex items-center gap-1.5 text-xs text-zinc-500 dark:text-zinc-400">
      <FileText class="h-3.5 w-3.5" />
      引用来源（{{ sources.length }}）
    </div>
    <CollapsibleRoot v-for="(src, sIdx) in sources" :key="sIdx" class="mb-1 last:mb-0">
      <CollapsibleTrigger
        class="flex w-full items-center gap-2 rounded-xl border px-3 py-2 text-left text-sm transition-all duration-200 ease-out [&[data-state=open]>svg]:rotate-180"
        :class="src.cited
          ? 'border-emerald-300 bg-emerald-50 text-zinc-800 hover:bg-emerald-100/70 dark:border-emerald-700/60 dark:bg-emerald-900/20 dark:text-zinc-100'
          : 'border-zinc-200/80 bg-white text-zinc-800 hover:bg-zinc-50 dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100 dark:hover:bg-zinc-700'"
      >
        <span class="shrink-0 text-xs text-zinc-400">[{{ sIdx + 1 }}]</span>
        <span class="flex-1 truncate">{{ src.file_name || '未知文档' }}</span>
        <AppTag v-if="src.cited" variant="success">已引用</AppTag>
        <AppTag v-if="src.rerank_score != null" variant="warning">精排 {{ (src.rerank_score * 100).toFixed(0) }}</AppTag>
        <AppTag variant="info">相关度 {{ (src.score * 100).toFixed(0) }}%</AppTag>
        <ChevronDown class="h-4 w-4 shrink-0 text-zinc-400 transition-all duration-200 ease-out" />
      </CollapsibleTrigger>
      <CollapsibleContent
        class="mt-1 max-h-[200px] overflow-y-auto whitespace-pre-wrap rounded-xl bg-zinc-50 px-3 py-2 text-sm leading-relaxed text-zinc-500 dark:bg-zinc-800/60 dark:text-zinc-400"
      >
        {{ src.content }}
      </CollapsibleContent>
    </CollapsibleRoot>
  </div>
</template>

<script setup lang="ts">
/**
 * RAG 引用来源卡片列表：被消息气泡（ChatMessageItem）与流式 live 气泡（ChatMessageList）
 * 复用，避免来源渲染逻辑分叉。命中来源（cited）高亮为绿色。
 */
import { CollapsibleRoot, CollapsibleTrigger, CollapsibleContent } from 'radix-vue'
import { FileText, ChevronDown } from 'lucide-vue-next'
import { AppTag } from '@/components/ui'
import type { RagSource } from '@/types'

defineProps<{ sources: RagSource[] }>()
</script>
