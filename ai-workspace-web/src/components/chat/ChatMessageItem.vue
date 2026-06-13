<template>
  <div class="group flex items-start gap-3" :class="msg.role === 'user' ? 'flex-row-reverse' : ''">
    <AppAvatar :icon="msg.role === 'user' ? User : Bot" :variant="msg.role === 'user' ? 'light' : 'dark'" />
    <div class="flex max-w-[72%] flex-col gap-1" :class="msg.role === 'user' ? 'items-end' : 'items-start'">
      <!-- 气泡正文。超长消息（Feature 3）折叠：collapsed 时限高 + 底部渐隐遮罩 -->
      <div
        class="relative break-words rounded-2xl px-4 py-3 text-sm leading-relaxed"
        :class="[
          msg.role === 'user'
            ? 'bg-zinc-100 text-zinc-900 dark:bg-zinc-700 dark:text-zinc-100'
            : 'bg-zinc-100/50 text-zinc-800 dark:bg-zinc-800 dark:text-zinc-100',
          collapsible && collapsed ? 'max-h-[460px] overflow-hidden' : ''
        ]"
      >
        <MarkdownView v-if="msg.role === 'assistant'" :content="msg.content" />
        <span v-else class="whitespace-pre-wrap">{{ msg.content }}</span>

        <!-- 折叠态底部渐隐遮罩，提示下方还有内容 -->
        <div
          v-if="collapsible && collapsed"
          class="pointer-events-none absolute inset-x-0 bottom-0 h-16 rounded-b-2xl"
          :class="msg.role === 'user'
            ? 'bg-gradient-to-t from-zinc-100 to-transparent dark:from-zinc-700'
            : 'bg-gradient-to-t from-zinc-100/90 to-transparent dark:from-zinc-800'"
        />
      </div>

      <!-- RAG 引用来源：assistant 消息携带 sources 时折叠展示（命中来源高亮） -->
      <RagSources v-if="sources.length" :sources="sources" class="max-w-[72%]" />

      <!-- Feature 3：Show More / Show Less 切换（仅超长消息出现） -->
      <button
        v-if="collapsible"
        class="flex items-center gap-1 rounded-lg px-2 py-0.5 text-xs font-medium text-zinc-500 transition-colors duration-200 hover:bg-zinc-100 hover:text-zinc-800 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-200"
        @click="collapsed = !collapsed"
      >
        <component :is="collapsed ? ChevronDown : ChevronUp" class="h-3.5 w-3.5" />
        {{ collapsed ? `Show More（${lineCount} 行）` : 'Show Less' }}
      </button>

      <!-- Feature 2：含 Artifact 时显示「在面板中打开」入口 -->
      <button
        v-if="artifacts.length"
        class="flex items-center gap-1.5 rounded-lg border border-zinc-200 px-2.5 py-1 text-xs font-medium text-zinc-600 transition-colors duration-200 hover:border-zinc-300 hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800"
        @click="$emit('open-artifact', artifacts)"
      >
        <LayoutPanelLeft class="h-3.5 w-3.5" />
        在 Artifact 面板打开<span class="text-zinc-400">· {{ artifacts.map((a) => a.title).join('、') }}</span>
      </button>

      <!-- 消息操作栏：悬停淡入 -->
      <div
        class="flex items-center gap-0.5 opacity-0 transition-opacity duration-200 ease-out group-hover:opacity-100 focus-within:opacity-100"
        :class="msg.role === 'user' ? 'flex-row-reverse' : ''"
      >
        <AppTooltip content="复制">
          <button class="rounded-lg p-1.5 text-zinc-400 transition-colors duration-200 hover:bg-zinc-100 hover:text-zinc-700 dark:hover:bg-zinc-800 dark:hover:text-zinc-200" @click="$emit('copy', msg)">
            <Copy class="h-3.5 w-3.5" />
          </button>
        </AppTooltip>
        <AppTooltip v-if="msg.role === 'assistant'" content="重新生成">
          <button
            class="rounded-lg p-1.5 text-zinc-400 transition-colors duration-200 hover:bg-zinc-100 hover:text-zinc-700 disabled:cursor-not-allowed disabled:opacity-40 dark:hover:bg-zinc-800 dark:hover:text-zinc-200"
            :disabled="busy"
            @click="$emit('regenerate')"
          >
            <RefreshCw class="h-3.5 w-3.5" />
          </button>
        </AppTooltip>
        <AppTooltip content="删除">
          <button class="rounded-lg p-1.5 text-zinc-400 transition-colors duration-200 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-950/40 dark:hover:text-red-400" @click="$emit('delete')">
            <Trash2 class="h-3.5 w-3.5" />
          </button>
        </AppTooltip>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 单条消息气泡（用户/助手通用），被普通列表与虚拟滚动列表复用，避免两套渲染逻辑分叉。
 *
 * 承载三件事：
 * 1. Feature 3 折叠：正文行数超过 FOLD_LINES（300）时默认折叠，提供 Show More / Show Less。
 * 2. Feature 2 入口：检测到 Artifact 时显示「在 Artifact 面板打开」按钮（emit open-artifact）。
 * 3. 消息操作栏：复制 / 重新生成 / 删除（emit 给父级处理，本组件不持有业务状态）。
 */
import { computed, ref } from 'vue'
import { Bot, User, Copy, RefreshCw, Trash2, ChevronDown, ChevronUp, LayoutPanelLeft } from 'lucide-vue-next'
import { AppAvatar, AppTooltip } from '@/components/ui'
import MarkdownView from '@/components/MarkdownView.vue'
import RagSources from './RagSources.vue'
import { extractArtifacts, type Artifact } from '@/utils/artifacts'
import type { ChatMessage, RagSource } from '@/types'

/** Feature 3 折叠阈值：正文超过 300 行自动折叠 */
const FOLD_LINES = 300

const props = defineProps<{ msg: ChatMessage; busy?: boolean }>()
defineEmits<{
  copy: [msg: ChatMessage]
  regenerate: []
  delete: []
  'open-artifact': [artifacts: Artifact[]]
}>()

const collapsed = ref(true)

const lineCount = computed(() => (props.msg.content ? props.msg.content.split('\n').length : 0))
const collapsible = computed(() => lineCount.value > FOLD_LINES)

// 仅助手消息检测 Artifact（用户输入不进面板）
const artifacts = computed<Artifact[]>(() =>
  props.msg.role === 'assistant' ? extractArtifacts(props.msg.content) : []
)

// RAG 引用来源（仅 assistant 消息携带）
const sources = computed<RagSource[]>(() =>
  props.msg.role === 'assistant' && props.msg.sources ? props.msg.sources : []
)
</script>
