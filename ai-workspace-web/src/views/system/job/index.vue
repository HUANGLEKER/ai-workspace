<template>
  <div class="pb-6">
    <div class="mb-4 flex items-start justify-between gap-4">
      <h2 class="text-lg font-semibold text-zinc-800">定时任务</h2>
      <AppButton v-if="activeTab === 'jobs'" variant="primary" :icon="Plus" @click="openDialog()">新建任务</AppButton>
      <AppButton v-else variant="danger" :icon="Trash2" @click="handleCleanLogs">清空日志</AppButton>
    </div>

    <AppCard>
      <AppTabs v-model="activeTab" :tabs="[{ name: 'jobs', label: '任务' }, { name: 'logs', label: '执行日志' }]" @change="onTabChange">
        <!-- 任务列表 -->
        <template #jobs>
          <div class="mb-4 flex flex-wrap items-center gap-3">
            <AppSearch v-model="jobSearch" placeholder="搜索任务名..." class="!w-56 max-w-56" @enter="loadJobs" />
            <AppButton variant="primary" :icon="Search" @click="loadJobs">搜索</AppButton>
          </div>
          <AppTable :columns="jobColumns" :data="jobs" :loading="jobLoading">
            <template #cell-status="{ row }">
              <AppSwitch :model-value="row.status === 0" @change="(v: boolean) => toggleStatus(row, v)" />
            </template>
            <template #cell-actions="{ row }">
              <div class="flex justify-center gap-1">
                <AppButton size="sm" variant="ghost" :icon="Play" @click="handleRun(row.id!)">执行</AppButton>
                <AppButton size="sm" variant="ghost" :icon="Pencil" @click="openDialog(row)">编辑</AppButton>
                <AppButton size="sm" variant="danger-ghost" :icon="Trash2" @click="handleDelete(row.id!)">删除</AppButton>
              </div>
            </template>
          </AppTable>
          <AppPagination
            v-if="jobTotal > 0"
            v-model:page="jobPage"
            v-model:size="jobSize"
            :total="jobTotal"
            class="mt-4"
            @change="loadJobs"
          />
        </template>

        <!-- 执行日志 -->
        <template #logs>
          <AppTable :columns="logColumns" :data="logs" :loading="logLoading">
            <template #cell-status="{ row }">
              <AppTag :variant="row.status === 0 ? 'success' : 'danger'">{{ row.status === 0 ? '成功' : '失败' }}</AppTag>
            </template>
            <template #cell-createTime="{ row }">{{ formatDate(row.createTime!) }}</template>
          </AppTable>
          <AppPagination
            v-if="logTotal > 0"
            v-model:page="logPage"
            v-model:size="logSize"
            :total="logTotal"
            class="mt-4"
            @change="loadLogs"
          />
        </template>
      </AppTabs>
    </AppCard>

    <!-- 新增/编辑任务 -->
    <AppDialog v-model="dialogVisible" :title="editing ? '编辑任务' : '新建任务'" width="520px" @close="resetForm">
      <AppFormItem label="任务名称" required>
        <AppInput v-model="form.jobName" placeholder="请输入任务名称" />
      </AppFormItem>
      <AppFormItem label="调用目标" required>
        <AppSelect v-model="form.invokeTarget" :options="handlers.map(h => ({ label: h, value: h }))" placeholder="选择任务处理器" />
      </AppFormItem>
      <AppFormItem label="Cron表达式" required>
        <AppInput v-model="form.cronExpression" placeholder="6段：秒 分 时 日 月 周，如 0 0/5 * * * ?" />
      </AppFormItem>
      <AppFormItem label="参数">
        <AppInput v-model="form.jobParams" placeholder="可选，传给处理器的字符串" />
      </AppFormItem>
      <AppFormItem label="备注">
        <AppInput v-model="form.remark" placeholder="可选" />
      </AppFormItem>
      <AppFormItem label="状态">
        <AppRadioGroup v-model="form.status" numeric :options="[{ label: '运行', value: 0 }, { label: '暂停', value: 1 }]" />
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
 * 定时任务管理页（仅管理员）：任务分页/搜索/启停/立即执行/CRUD，
 * 执行日志分页与全量清空（物理删除）。Cron 为 6 段式（含秒字段）。
 * 切换 Tab 时按需加载对应数据。
 */
import { ref, reactive, onMounted } from 'vue'
import { Plus, Search, Pencil, Trash2, Play } from 'lucide-vue-next'
import {
  pageJobs, listHandlers, addJob, updateJob, deleteJob, changeJobStatus, runJob,
  pageJobLogs, cleanJobLogs, type SysJob, type SysJobLog
} from '@/api/job'
import {
  AppButton, AppCard, AppTabs, AppSearch, AppTable, AppSwitch, AppTag, AppDialog,
  AppFormItem, AppInput, AppSelect, AppRadioGroup, AppPagination,
  toast, confirm, type TableColumn
} from '@/components/ui'

const activeTab = ref('jobs')

// 任务相关状态
const jobs = ref<SysJob[]>([])
const jobLoading = ref(false)
const jobPage = ref(1)
const jobSize = ref(10)
const jobTotal = ref(0)
const jobSearch = ref('')
/** 可用的 JobHandler 名称列表，由后端枚举 */
const handlers = ref<string[]>([])

// 执行日志相关状态
const logs = ref<SysJobLog[]>([])
const logLoading = ref(false)
const logPage = ref(1)
const logSize = ref(10)
const logTotal = ref(0)

const jobColumns: TableColumn[] = [
  { key: 'id', label: 'ID', width: '64px', align: 'center' },
  { key: 'jobName', label: '任务名称', width: '150px' },
  { key: 'invokeTarget', label: '调用目标', width: '130px' },
  { key: 'cronExpression', label: 'Cron', width: '150px' },
  { key: 'remark', label: '备注' },
  { key: 'status', label: '状态', width: '90px', align: 'center' },
  { key: 'actions', label: '操作', width: '230px', align: 'center' }
]

const logColumns: TableColumn[] = [
  { key: 'id', label: 'ID', width: '64px', align: 'center' },
  { key: 'jobName', label: '任务名称', width: '150px' },
  { key: 'invokeTarget', label: '调用目标', width: '130px' },
  { key: 'status', label: '结果', width: '90px', align: 'center' },
  { key: 'jobMessage', label: '信息' },
  { key: 'costMs', label: '耗时(ms)', width: '100px', align: 'center' },
  { key: 'createTime', label: '执行时间', width: '170px', align: 'center' }
]

const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref<SysJob | null>(null)
const form = reactive<SysJob>({ jobName: '', invokeTarget: '', cronExpression: '', jobParams: '', remark: '', status: 1 })

onMounted(() => { loadJobs(); loadHandlers() })

async function loadHandlers() {
  try { handlers.value = await listHandlers() } catch { handlers.value = [] }
}

async function loadJobs() {
  jobLoading.value = true
  try {
    const res = await pageJobs({ page: jobPage.value, size: jobSize.value, jobName: jobSearch.value || undefined })
    jobs.value = res.records
    jobTotal.value = res.total
  } catch { jobs.value = [] } finally { jobLoading.value = false }
}

async function loadLogs() {
  logLoading.value = true
  try {
    const res = await pageJobLogs({ page: logPage.value, size: logSize.value })
    logs.value = res.records
    logTotal.value = res.total
  } catch { logs.value = [] } finally { logLoading.value = false }
}

function onTabChange(name: string) {
  if (name === 'logs') loadLogs()
  else loadJobs()
}

function openDialog(row?: SysJob) {
  editing.value = row || null
  if (row) Object.assign(form, row)
  else Object.assign(form, { jobName: '', invokeTarget: '', cronExpression: '', jobParams: '', remark: '', status: 1 })
  dialogVisible.value = true
}
function resetForm() {
  editing.value = null
}

async function handleSubmit() {
  if (!form.jobName.trim() || !form.invokeTarget || !form.cronExpression.trim()) {
    toast.warning('请填写任务名称、调用目标和 Cron 表达式')
    return
  }
  submitting.value = true
  try {
    if (editing.value) { await updateJob({ ...form, id: editing.value.id }); toast.success('修改成功') }
    else { await addJob(form); toast.success('创建成功') }
    dialogVisible.value = false
    loadJobs()
  } catch { } finally { submitting.value = false }
}

async function handleDelete(id: number) {
  const ok = await confirm({ title: '删除确认', message: '确定删除该任务吗？', confirmText: '确定删除', danger: true })
  if (!ok) return
  try { await deleteJob(id); toast.success('删除成功'); loadJobs() } catch { }
}

async function toggleStatus(row: SysJob, running: boolean) {
  // status: 0=运行/调度中，1=暂停；开关绑定 status===0
  const status = running ? 0 : 1
  try {
    await changeJobStatus(row.id!, status)
    row.status = status
    toast.success(running ? '已启动调度' : '已暂停')
  } catch { }
}

async function handleRun(id: number) {
  try { await runJob(id); toast.success('已触发执行，稍后可在执行日志查看') } catch { }
}

async function handleCleanLogs() {
  const ok = await confirm({ title: '清空确认', message: '确定清空所有执行日志吗？', confirmText: '确定清空', danger: true })
  if (!ok) return
  try { await cleanJobLogs(); toast.success('已清空'); loadLogs() } catch { }
}

const formatDate = (d: string) => d ? new Date(d).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-') : '—'
</script>
