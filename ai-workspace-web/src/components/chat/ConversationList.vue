<template>
  <!--
    会话列表项（Chat / RAG 共用）：选中高亮、双击/铅笔内联重命名、悬停删除、折叠态仅图标。
    重命名内联编辑态由本组件内部持有；校验通过后才向上 emit('rename', session, title)。
  -->
  <div
    v-for="session in sessions"
    :key="session.id"
    class="group mb-0.5 flex cursor-pointer items-center gap-2 rounded-xl px-3 py-2.5 text-sm transition-all duration-200 ease-out"
    :class="[
      currentId === session.id
        ? 'bg-primary/10 font-medium text-primary dark:bg-primary/15'
        : 'text-zinc-500 hover:bg-zinc-100/50 hover:text-zinc-800 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100',
      collapsed ? 'justify-center px-0' : ''
    ]"
    @click="$emit('select', session)"
    @dblclick="!collapsed && startRename(session)"
  >
    <AppTooltip v-if="collapsed" :content="session.title" side="right">
      <component :is="icon" class="h-4 w-4 shrink-0" />
    </AppTooltip>
    <component :is="icon" v-else class="h-4 w-4 shrink-0" />

    <!-- 重命名输入态 -->
    <input
      v-if="!collapsed && renamingId === session.id"
      ref="renameInputRef"
      v-model="renameText"
      class="min-w-0 flex-1 rounded-md border border-zinc-300 bg-white px-1.5 py-0.5 text-sm text-zinc-900 outline-none focus:border-primary dark:border-zinc-600 dark:bg-zinc-800 dark:text-zinc-100"
      @click.stop
      @keydown.enter.prevent="commitRename(session)"
      @keydown.esc.prevent="cancelRename"
      @blur="commitRename(session)"
    />
    <span v-else-if="!collapsed" class="flex-1 truncate">{{ session.title }}</span>

    <template v-if="!collapsed && renamingId !== session.id">
      <button
        class="shrink-0 rounded-lg p-0.5 opacity-0 transition-all duration-200 ease-out hover:bg-zinc-200 group-hover:opacity-100 dark:hover:bg-zinc-700"
        @click.stop="startRename(session)"
      >
        <Pencil class="h-3.5 w-3.5" />
      </button>
      <button
        class="shrink-0 rounded-lg p-0.5 opacity-0 transition-all duration-200 ease-out hover:bg-zinc-200 group-hover:opacity-100 dark:hover:bg-zinc-700"
        @click.stop="$emit('delete', session.id)"
      >
        <Trash2 class="h-3.5 w-3.5" />
      </button>
    </template>
  </div>

  <AppEmpty v-if="showEmpty" :description="emptyText" />
  <AppLoading v-if="loading" overlay />
</template>

<script setup lang="ts" generic="T extends { id: number; title: string }">
import { ref, nextTick, type Component } from 'vue'
import { Pencil, Trash2 } from 'lucide-vue-next'
import { AppEmpty, AppLoading, AppTooltip } from '@/components/ui'

defineProps<{
  sessions: T[]
  currentId: number | null
  collapsed: boolean
  icon: Component
  loading?: boolean
  showEmpty?: boolean
  emptyText?: string
}>()

const emit = defineEmits<{ select: [T]; rename: [T, string]; delete: [number] }>()

const renamingId = ref<number | null>(null)
const renameText = ref('')
const renameInputRef = ref<HTMLInputElement | HTMLInputElement[] | null>(null)

function startRename(session: T) {
  renamingId.value = session.id
  renameText.value = session.title
  nextTick(() => {
    const el = Array.isArray(renameInputRef.value) ? renameInputRef.value[0] : renameInputRef.value
    el?.focus()
    el?.select()
  })
}

function cancelRename() {
  renamingId.value = null
  renameText.value = ''
}

function commitRename(session: T) {
  if (renamingId.value !== session.id) return
  const title = renameText.value.trim()
  renamingId.value = null
  if (!title || title === session.title) return
  emit('rename', session, title)
}
</script>
