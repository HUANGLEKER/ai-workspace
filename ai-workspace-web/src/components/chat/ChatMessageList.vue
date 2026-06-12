<template>
  <!-- 普通模式：消息量未超阈值，保留 VueUse Motion 入场动画与原生滚动容器 -->
  <div
    v-if="!virtual"
    ref="containerRef"
    class="flex flex-1 flex-col gap-5 overflow-y-auto p-5"
    @scroll.passive="onScroll"
  >
    <TransitionGroup tag="div" name="msg" class="contents">
      <ChatMessageItem
        v-for="(msg, idx) in messages"
        :key="msg.id ?? `local-${idx}`"
        v-motion="messageMotion(msg.role, idx === noAnimateIdx)"
        :msg="msg"
        :busy="busy"
        @copy="$emit('copy', msg)"
        @regenerate="$emit('regenerate', idx)"
        @delete="$emit('delete', idx)"
        @open-artifact="(a) => $emit('open-artifact', a)"
      />
    </TransitionGroup>

    <!-- 思考态 / 流式态（普通模式） -->
    <ThinkingIndicator v-if="showThinking" :phases="phases" :in-progress="inProgress" />
    <div v-if="showStream" v-motion="assistantMessageMotion" class="flex items-start gap-3">
      <AppAvatar :icon="Bot" variant="dark" />
      <div class="max-w-[72%] break-words rounded-2xl bg-zinc-100/50 px-4 py-3 text-sm leading-relaxed text-zinc-800 dark:bg-zinc-800 dark:text-zinc-100">
        <MarkdownView :content="streamDisplay" :caret="caretFading ? 'fade' : 'blink'" />
      </div>
    </div>

    <div v-if="empty" class="flex flex-1 flex-col items-center justify-center gap-2 text-zinc-400 dark:text-zinc-500">
      <MessageSquare class="h-10 w-10 text-zinc-200 dark:text-zinc-700" />
      <p class="text-sm">发送消息开始对话</p>
    </div>
  </div>

  <!-- 虚拟滚动模式：消息量超阈值（Feature 4），仅渲染视口内消息，关闭逐条入场动画以保性能 -->
  <DynamicScroller
    v-else
    ref="scrollerRef"
    :items="items"
    :min-item-size="88"
    key-field="key"
    class="chat-virtual-scroller flex-1 p-5"
    @scroll.passive="onVirtualScroll"
  >
    <template #default="{ item, active }">
      <DynamicScrollerItem :item="item" :active="active" :size-dependencies="[item.content]" :data-index="item.index">
        <div class="pb-5">
          <ChatMessageItem
            v-if="item.kind === 'msg'"
            :msg="item.msg"
            :busy="busy"
            @copy="$emit('copy', item.msg)"
            @regenerate="$emit('regenerate', item.index)"
            @delete="$emit('delete', item.index)"
            @open-artifact="(a) => $emit('open-artifact', a)"
          />
          <template v-else>
            <ThinkingIndicator v-if="showThinking" :phases="phases" :in-progress="inProgress" />
            <div v-if="showStream" class="flex items-start gap-3">
              <AppAvatar :icon="Bot" variant="dark" />
              <div class="max-w-[72%] break-words rounded-2xl bg-zinc-100/50 px-4 py-3 text-sm leading-relaxed text-zinc-800 dark:bg-zinc-800 dark:text-zinc-100">
                <MarkdownView :content="streamDisplay" :caret="caretFading ? 'fade' : 'blink'" />
              </div>
            </div>
          </template>
        </div>
      </DynamicScrollerItem>
    </template>
  </DynamicScroller>
</template>

<script setup lang="ts">
/**
 * 聊天消息列表：统一封装「普通渲染」与「虚拟滚动」两种模式，对外暴露一致的滚动控制。
 *
 * 模式切换（Feature 4）：messages.length 超过 VIRTUAL_THRESHOLD（默认 1000）时启用
 * vue-virtual-scroller 的 DynamicScroller，仅渲染视口内消息，避免上千条 Markdown 节点
 * 同时挂载拖垮页面；未超阈值时沿用带 VueUse Motion 入场动画的普通列表。
 *
 * 两种模式下都保持：
 * - SSE 流式体验：把「思考态 + 流式气泡」作为列表末尾的一个 live 伪条目（虚拟模式）
 *   或紧随列表渲染（普通模式），内容随 streamDisplay 实时刷新。
 * - 自动滚动：pinned（用户是否锚定底部）+ scheduleScroll（rAF 节流跟随），
 *   普通模式直接写 scrollTop，虚拟模式调用 scroller.scrollToBottom()。用户上滚查看历史
 *   时自动暂停跟随，回到底部容差区恢复。
 */
import { ref, computed, nextTick, watch, onBeforeUnmount } from 'vue'
import { DynamicScroller, DynamicScrollerItem } from 'vue-virtual-scroller'
import { Bot, MessageSquare } from 'lucide-vue-next'
import { AppAvatar } from '@/components/ui'
import MarkdownView from '@/components/MarkdownView.vue'
import ChatMessageItem from './ChatMessageItem.vue'
import ThinkingIndicator from './ThinkingIndicator.vue'
import { messageMotion, assistantMessageMotion } from '@/composables/useMessageMotion'
import type { ChatMessage } from '@/types'
import type { ThinkingPhase } from '@/composables/useThinkingPhases'
import type { Artifact } from '@/utils/artifacts'

/** Feature 4：超过该消息数启用虚拟滚动 */
const VIRTUAL_THRESHOLD = 1000
/** 底部容差：距底 ≤ 该值视为「锚定」 */
const BOTTOM_THRESHOLD = 80

const props = defineProps<{
  messages: ChatMessage[]
  busy: boolean
  noAnimateIdx: number
  // 流式 / 思考态
  streaming: boolean
  caretFading: boolean
  thinking: boolean
  streamDisplay: string
  phases: ThinkingPhase[]
  inProgress: boolean
}>()

defineEmits<{
  copy: [msg: ChatMessage]
  regenerate: [idx: number]
  delete: [idx: number]
  'open-artifact': [artifacts: Artifact[]]
}>()

const virtual = computed(() => props.messages.length > VIRTUAL_THRESHOLD)

// 思考态：流已开始但首 token 尚未到达（thinking 为真且无流式内容）
const showThinking = computed(() => props.thinking && !props.streamDisplay && !props.caretFading)
const showStream = computed(() => (props.streaming || props.caretFading) && !!props.streamDisplay)
const live = computed(() => showThinking.value || showStream.value)
const empty = computed(() => props.messages.length === 0 && !live.value)

// 虚拟模式条目：消息 + 末尾 live 伪条目（思考/流式）
const items = computed(() => {
  const arr = props.messages.map((msg, index) => ({
    key: msg.id != null ? `m-${msg.id}` : `local-${index}`,
    kind: 'msg' as const,
    msg,
    index,
    content: msg.content
  }))
  if (live.value) {
    arr.push({
      key: '__live__',
      kind: 'live' as any,
      msg: undefined as any,
      index: -1,
      content: props.streamDisplay
    })
  }
  return arr
})

// ─── 滚动跟随：普通模式直接写 scrollTop；虚拟模式走 scroller API ───
const containerRef = ref<HTMLElement>()
const scrollerRef = ref<any>()
const pinned = ref(true)
let writeRaf = 0
let readRaf = 0

function virtualEl(): HTMLElement | undefined {
  return scrollerRef.value?.$el as HTMLElement | undefined
}

function isAtBottom(el: HTMLElement) {
  return el.scrollHeight - el.scrollTop - el.clientHeight <= BOTTOM_THRESHOLD
}

function onScroll() {
  if (readRaf) return
  readRaf = requestAnimationFrame(() => {
    readRaf = 0
    const el = containerRef.value
    if (el) pinned.value = isAtBottom(el)
  })
}

function onVirtualScroll() {
  if (readRaf) return
  readRaf = requestAnimationFrame(() => {
    readRaf = 0
    const el = virtualEl()
    if (el) pinned.value = isAtBottom(el)
  })
}

/** 立即滚到底部（切换会话/发送/点击回到底部）。force 时强制重新锚定。 */
async function scrollToBottom(force = true) {
  await nextTick()
  if (force) pinned.value = true
  if (virtual.value) {
    scrollerRef.value?.scrollToBottom?.()
    return
  }
  const el = containerRef.value
  if (el) el.scrollTop = el.scrollHeight
}

/** 流式期间节流跟随：仅锚定时生效，rAF 合并写入 */
function scheduleScroll() {
  if (!pinned.value || writeRaf) return
  writeRaf = requestAnimationFrame(() => {
    writeRaf = 0
    if (!pinned.value) return
    if (virtual.value) {
      scrollerRef.value?.scrollToBottom?.()
    } else {
      const el = containerRef.value
      if (el) el.scrollTop = el.scrollHeight
    }
  })
}

// 模式切换（普通↔虚拟）后，DOM 容器更替，若仍锚定则补一次滚动到底
watch(virtual, () => {
  if (pinned.value) scrollToBottom()
})

onBeforeUnmount(() => {
  if (writeRaf) cancelAnimationFrame(writeRaf)
  if (readRaf) cancelAnimationFrame(readRaf)
})

defineExpose({ pinned, scrollToBottom, scheduleScroll })
</script>
