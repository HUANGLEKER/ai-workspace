<template>
  <aside
    class="fixed inset-y-0 left-0 z-[7000] flex h-full w-[260px] shrink-0 flex-col border-r border-line bg-canvas transition-all duration-200 ease-out md:static md:z-auto md:translate-x-0"
    :class="[
      collapsed ? 'md:w-[72px]' : 'md:w-[260px]',
      mobileOpen ? 'translate-x-0' : '-translate-x-full'
    ]"
  >
    <div class="flex h-14 items-center gap-2.5 border-b border-line px-4">
      <div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-primary">
        <ProductIcon class="h-4.5 w-4.5 text-primary-fg" />
      </div>
      <span v-if="!collapsed" class="truncate text-sm font-semibold text-zinc-800 dark:text-zinc-100">AI Workspace</span>
    </div>

    <nav class="flex-1 overflow-y-auto overflow-x-hidden px-3 py-3">
      <template v-for="group in visibleGroups" :key="group.label">
        <div
          v-if="group.label && !collapsed"
          class="mb-1 mt-4 px-3 text-xs font-medium text-zinc-400 first:mt-0 dark:text-zinc-500"
        >
          {{ group.label }}
        </div>
        <div v-else-if="group.label && collapsed" class="my-3 h-px bg-line" />

        <router-link
          v-for="item in group.items"
          :key="item.path"
          :to="item.path"
          class="mb-0.5 flex items-center gap-2.5 rounded-xl px-3 py-2 text-sm transition-all duration-200 ease-out"
          :class="[
            isActivePath(item.path)
              ? 'bg-primary/10 font-medium text-primary dark:bg-primary/15'
              : 'text-zinc-500 hover:bg-zinc-100/50 hover:text-zinc-800 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100',
            collapsed ? 'justify-center px-0' : ''
          ]"
        >
          <AppTooltip v-if="collapsed" :content="item.title" side="right">
            <component :is="item.icon" class="h-4 w-4 shrink-0" />
          </AppTooltip>
          <component :is="item.icon" v-else class="h-4 w-4 shrink-0" />
          <span v-if="!collapsed" class="truncate">{{ item.title }}</span>
        </router-link>
      </template>
    </nav>

    <button
      class="hidden h-11 items-center justify-center border-t border-line text-zinc-500 transition-all duration-200 ease-out hover:bg-zinc-100/50 hover:text-zinc-800 md:flex dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-100"
      @click="$emit('toggle')"
    >
      <PanelLeft class="h-4 w-4" />
    </button>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { PanelLeft } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { AppTooltip } from '@/components/ui'
import { getVisibleNavGroups, isNavItemActive, productIcon as ProductIcon } from '../navigation'

defineProps<{ collapsed: boolean; mobileOpen?: boolean }>()
defineEmits<{ toggle: [] }>()

const route = useRoute()
const authStore = useAuthStore()

const visibleGroups = computed(() => getVisibleNavGroups(authStore.isAdmin))
const isActivePath = (path: string) => isNavItemActive(route.path, path)
</script>
