<template>
  <div class="page-wrapper">
    <div class="page-header">
      <h2>定时任务</h2>
      <el-button v-if="activeTab === 'jobs'" type="primary" :icon="Plus" @click="openDialog()">新建任务</el-button>
      <el-button v-else type="danger" :icon="Delete" @click="handleCleanLogs">清空日志</el-button>
    </div>

    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- 任务列表 -->
        <el-tab-pane label="任务" name="jobs">
          <div class="toolbar">
            <el-input v-model="jobSearch" placeholder="搜索任务名..." :prefix-icon="Search" clearable
              style="width: 220px" @keyup.enter="loadJobs" />
            <el-button type="primary" :icon="Search" @click="loadJobs">搜索</el-button>
          </div>
          <el-table :data="jobs" v-loading="jobLoading" stripe border style="margin-top:12px">
            <el-table-column label="ID" prop="id" width="64" align="center" />
            <el-table-column label="任务名称" prop="jobName" width="150" show-overflow-tooltip />
            <el-table-column label="调用目标" prop="invokeTarget" width="130" />
            <el-table-column label="Cron" prop="cronExpression" width="150" />
            <el-table-column label="备注" prop="remark" min-width="140" show-overflow-tooltip />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-switch :model-value="row.status === 0" active-text="运行" inactive-text="暂停" inline-prompt
                  @change="(v: string | number | boolean) => toggleStatus(row as SysJob, v as boolean)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" align="center" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="VideoPlay" @click="handleRun(row.id)">执行</el-button>
                <el-button link :icon="Edit" @click="openDialog(row as SysJob)">编辑</el-button>
                <el-button link type="danger" :icon="Delete" @click="handleDelete(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-if="jobTotal > 0" v-model:current-page="jobPage" v-model:page-size="jobSize"
            :total="jobTotal" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
            class="pagination" @change="loadJobs" />
        </el-tab-pane>

        <!-- 执行日志 -->
        <el-tab-pane label="执行日志" name="logs">
          <el-table :data="logs" v-loading="logLoading" stripe border>
            <el-table-column label="ID" prop="id" width="64" align="center" />
            <el-table-column label="任务名称" prop="jobName" width="150" show-overflow-tooltip />
            <el-table-column label="调用目标" prop="invokeTarget" width="130" />
            <el-table-column label="结果" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">
                  {{ row.status === 0 ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="信息" prop="jobMessage" min-width="180" show-overflow-tooltip />
            <el-table-column label="耗时(ms)" prop="costMs" width="100" align="center" />
            <el-table-column label="执行时间" prop="createTime" width="170" align="center">
              <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
            </el-table-column>
          </el-table>
          <el-pagination v-if="logTotal > 0" v-model:current-page="logPage" v-model:page-size="logSize"
            :total="logTotal" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
            class="pagination" @change="loadLogs" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 新增/编辑任务 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑任务' : '新建任务'" width="520px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="任务名称" prop="jobName">
          <el-input v-model="form.jobName" placeholder="请输入任务名称" clearable />
        </el-form-item>
        <el-form-item label="调用目标" prop="invokeTarget">
          <el-select v-model="form.invokeTarget" placeholder="选择任务处理器" style="width:100%">
            <el-option v-for="h in handlers" :key="h" :label="h" :value="h" />
          </el-select>
        </el-form-item>
        <el-form-item label="Cron表达式" prop="cronExpression">
          <el-input v-model="form.cronExpression" placeholder="6段：秒 分 时 日 月 周，如 0 0/5 * * * ?" clearable />
        </el-form-item>
        <el-form-item label="参数">
          <el-input v-model="form.jobParams" placeholder="可选，传给处理器的字符串" clearable />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" placeholder="可选" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">运行</el-radio>
            <el-radio :value="1">暂停</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ editing ? '保存修改' : '确认创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Search, Edit, Delete, VideoPlay } from '@element-plus/icons-vue'
import {
  pageJobs, listHandlers, addJob, updateJob, deleteJob, changeJobStatus, runJob,
  pageJobLogs, cleanJobLogs, type SysJob, type SysJobLog
} from '@/api/job'

const activeTab = ref('jobs')

// jobs
const jobs = ref<SysJob[]>([])
const jobLoading = ref(false)
const jobPage = ref(1)
const jobSize = ref(10)
const jobTotal = ref(0)
const jobSearch = ref('')
const handlers = ref<string[]>([])

// logs
const logs = ref<SysJobLog[]>([])
const logLoading = ref(false)
const logPage = ref(1)
const logSize = ref(10)
const logTotal = ref(0)

const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref<SysJob | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<SysJob>({ jobName: '', invokeTarget: '', cronExpression: '', jobParams: '', remark: '', status: 1 })
const rules: FormRules = {
  jobName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  invokeTarget: [{ required: true, message: '请选择调用目标', trigger: 'change' }],
  cronExpression: [{ required: true, message: '请输入Cron表达式', trigger: 'blur' }]
}

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

function onTabChange(name: string | number) {
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
  formRef.value?.resetFields()
  editing.value = null
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editing.value) { await updateJob({ ...form, id: editing.value.id }); ElMessage.success('修改成功') }
    else { await addJob(form); ElMessage.success('创建成功') }
    dialogVisible.value = false
    loadJobs()
  } catch { } finally { submitting.value = false }
}

async function handleDelete(id: number) {
  await ElMessageBox.confirm('确定删除该任务吗？', '删除确认', { type: 'warning' }).catch(() => { throw new Error('cancel') })
  try { await deleteJob(id); ElMessage.success('删除成功'); loadJobs() } catch { }
}

async function toggleStatus(row: SysJob, running: boolean) {
  const status = running ? 0 : 1
  try {
    await changeJobStatus(row.id!, status)
    row.status = status
    ElMessage.success(running ? '已启动调度' : '已暂停')
  } catch { }
}

async function handleRun(id: number) {
  try { await runJob(id); ElMessage.success('已触发执行，稍后可在执行日志查看') } catch { }
}

async function handleCleanLogs() {
  await ElMessageBox.confirm('确定清空所有执行日志吗？', '清空确认', { type: 'warning' }).catch(() => { throw new Error('cancel') })
  try { await cleanJobLogs(); ElMessage.success('已清空'); loadLogs() } catch { }
}

const formatDate = (d: string) => d ? new Date(d).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-') : '—'
</script>

<style scoped>
.page-wrapper { padding-bottom: 20px; }
.toolbar { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.pagination { margin-top: 16px; justify-content: flex-end; }
</style>
