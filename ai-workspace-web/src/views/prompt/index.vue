<template>
  <div class="pb-6">
    <div class="mb-4 flex items-start justify-between gap-4">
      <h2 class="text-lg font-semibold text-zinc-800 dark:text-zinc-100">提示词中心</h2>
      <AppButton variant="primary" :icon="Plus" @click="openDialog()">新建提示词</AppButton>
    </div>

    <!-- 搜索栏 -->
    <div class="mb-4 flex flex-wrap items-center gap-3">
      <AppSearch v-model="keyword" placeholder="搜索标题..." class="!w-56 max-w-56" @enter="load" />
      <AppInput v-model="category" placeholder="按分类筛选" class="!w-40 max-w-40" @enter="load" />
      <AppButton variant="primary" :icon="Search" @click="load">搜索</AppButton>
      <AppButton @click="reset">重置</AppButton>
    </div>

    <!-- 提示词卡片网格 -->
    <div class="relative grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
      <div
        v-for="p in prompts"
        :key="p.id"
        class="flex flex-col rounded-2xl border border-zinc-200/80 dark:border-zinc-800 bg-white dark:bg-zinc-900 p-5 shadow-[0_10px_30px_-10px_rgba(0,0,0,0.04)] transition-all duration-200 ease-out hover:shadow-md"
      >
        <div class="flex items-center justify-between gap-2">
          <span class="truncate text-sm font-semibold text-zinc-800 dark:text-zinc-100">{{ p.title }}</span>
          <AppTag v-if="p.category" variant="info">{{ p.category }}</AppTag>
        </div>
        <p class="mt-1.5 text-sm text-zinc-500 dark:text-zinc-400">{{ p.description || '—' }}</p>
        <div class="mt-3 max-h-28 flex-1 overflow-auto whitespace-pre-wrap break-words rounded-xl bg-zinc-50 dark:bg-zinc-950 p-3 text-sm text-zinc-500 dark:text-zinc-400">{{ p.content }}</div>
        <div class="mt-3 flex justify-end gap-1">
          <AppButton size="sm" variant="ghost" :icon="Copy" @click="copy(p.content)">复制</AppButton>
          <AppButton size="sm" variant="ghost" :icon="Pencil" @click="openDialog(p)">编辑</AppButton>
          <AppButton size="sm" variant="danger-ghost" :icon="Trash2" @click="handleDelete(p.id!)">删除</AppButton>
        </div>
      </div>

      <AppEmpty v-if="!loading && prompts.length === 0" class="col-span-full py-16" description="还没有提示词，点击右上角新建" :icon="Sparkles" />
      <AppLoading v-if="loading" overlay />
    </div>

    <AppDialog v-model="dialogVisible" :title="editing ? '编辑提示词' : '新建提示词'" width="560px" @close="resetForm">
      <AppFormItem label="标题" required>
        <AppInput v-model="form.title" placeholder="请输入标题" />
      </AppFormItem>
      <AppFormItem label="分类">
        <AppInput v-model="form.category" placeholder="如：写作 / 编程" />
      </AppFormItem>
      <AppFormItem label="描述">
        <AppInput v-model="form.description" placeholder="一句话描述用途" />
      </AppFormItem>
      <AppFormItem label="内容" required>
        <AppTextarea v-model="form.content" :rows="6" placeholder="提示词正文，可用 {变量} 占位" />
      </AppFormItem>
      <template #footer>
        <AppButton @click="dialogVisible = false">取消</AppButton>
        <AppButton variant="primary" :loading="submitting" @click="handleSubmit">
          {{ editing ? '保存修改' : '确认创建' }}
        </AppButton>
      </template>
    </AppDialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 提示词中心页：Card Grid 列表（支持标题关键词和分类筛选），
 * 新建/编辑/删除，一键复制内容到剪贴板。
 */
import { ref, reactive, onMounted } from 'vue'
import { Plus, Search, Pencil, Trash2, Copy, Sparkles } from 'lucide-vue-next'
import { listPrompts, addPrompt, updatePrompt, deletePrompt, type Prompt } from '@/api/prompt'
import {
  AppButton, AppInput, AppSearch, AppTextarea, AppDialog, AppFormItem, AppTag,
  AppEmpty, AppLoading, toast, confirm
} from '@/components/ui'

const loading = ref(false)
const submitting = ref(false)
const prompts = ref<Prompt[]>([])
const keyword = ref('')
const category = ref('')
const dialogVisible = ref(false)
const editing = ref<Prompt | null>(null)

const form = reactive<Prompt>({ title: '', content: '', category: '', description: '' })

onMounted(load)

async function load() {
  loading.value = true
  try {
    prompts.value = await listPrompts({ keyword: keyword.value || undefined, category: category.value || undefined })
  } catch { prompts.value = [] } finally { loading.value = false }
}
function reset() {
  keyword.value = ''
  category.value = ''
  load()
}

function openDialog(row?: Prompt) {
  editing.value = row || null
  if (row) Object.assign(form, row)
  else Object.assign(form, { title: '', content: '', category: '', description: '' })
  dialogVisible.value = true
}
function resetForm() {
  editing.value = null
}

async function handleSubmit() {
  if (!form.title.trim() || !form.content.trim()) {
    toast.warning('请填写标题和内容')
    return
  }
  submitting.value = true
  try {
    if (editing.value) { await updatePrompt({ ...form, id: editing.value.id }); toast.success('修改成功') }
    else { await addPrompt(form); toast.success('创建成功') }
    dialogVisible.value = false
    load()
  } catch { } finally { submitting.value = false }
}

async function handleDelete(id: number) {
  const ok = await confirm({ title: '删除确认', message: '确定删除该提示词吗？', confirmText: '确定删除', danger: true })
  if (!ok) return
  try { await deletePrompt(id); toast.success('删除成功'); load() } catch { }
}

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    toast.success('已复制到剪贴板')
  } catch {
    toast.error('复制失败，请手动选择复制')
  }
}
</script>
