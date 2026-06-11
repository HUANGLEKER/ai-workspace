<template>
  <div class="relative overflow-x-auto rounded-xl border border-zinc-200/80">
    <table class="w-full border-collapse text-sm">
      <thead>
        <tr class="border-b border-zinc-200/80 bg-zinc-50">
          <th
            v-for="col in columns"
            :key="col.key"
            class="whitespace-nowrap px-4 py-3 text-xs font-medium text-zinc-500"
            :class="alignClass(col.align)"
            :style="col.width ? { width: col.width, minWidth: col.width } : undefined"
          >
            {{ col.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="(row, rIdx) in data"
          :key="rIdx"
          class="border-b border-zinc-200/80 last:border-b-0 transition-all duration-200 ease-out hover:bg-zinc-100/50"
        >
          <td
            v-for="col in columns"
            :key="col.key"
            class="px-4 py-3 text-zinc-800"
            :class="alignClass(col.align)"
          >
            <slot :name="`cell-${col.key}`" :row="row">
              <span class="break-words">{{ (row as Record<string, unknown>)[col.key] ?? '—' }}</span>
            </slot>
          </td>
        </tr>
      </tbody>
    </table>

    <AppEmpty v-if="!loading && data.length === 0" :description="emptyText" class="py-12" />
    <AppLoading v-if="loading" overlay />
  </div>
</template>

<script setup lang="ts" generic="T">
import AppEmpty from './AppEmpty.vue'
import AppLoading from './AppLoading.vue'

export interface TableColumn {
  key: string
  label: string
  width?: string
  align?: 'left' | 'center' | 'right'
}

withDefaults(defineProps<{ columns: TableColumn[]; data: T[]; loading?: boolean; emptyText?: string }>(), {
  emptyText: '暂无数据'
})

const alignClass = (a?: string) => (a === 'center' ? 'text-center' : a === 'right' ? 'text-right' : 'text-left')
</script>
