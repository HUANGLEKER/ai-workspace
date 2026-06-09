/**
 * 定时任务管理 API（仅管理员）
 *
 * invokeTarget 对应后端注册的 JobHandler bean 名称，通过 /job/handlers 枚举可用列表。
 * Cron 表达式为 Spring 6 段式（秒 分 时 日 月 周），由后端 CronExpression.isValidExpression 校验。
 * cleanJobLogs 为物理删除，sys_job_log 表不含 deleted 字段。
 */
import request from './request'

export interface SysJob {
  id?: number
  jobName: string
  jobGroup?: string
  /** 对应 JobHandler bean 名称，运行时由 JobHandlerRegistry 按名解析 */
  invokeTarget: string
  /** Spring 6 段 Cron 表达式：秒 分 时 日 月 周 */
  cronExpression: string
  jobParams?: string
  /** 0=运行/已调度，1=暂停 */
  status?: number
  remark?: string
  createTime?: string
}

export interface SysJobLog {
  id: number
  jobId: number
  jobName: string
  invokeTarget: string
  jobParams?: string
  /** 0=成功，1=失败 */
  status: number
  jobMessage?: string
  exceptionInfo?: string
  costMs?: number
  createTime: string
}

export interface PageResult<T> {
  records: T[]
  total: number
}

export const pageJobs = (params: { page: number; size: number; jobName?: string }) =>
  request.get<unknown, PageResult<SysJob>>('/job/page', { params })
export const listHandlers = () => request.get<unknown, string[]>('/job/handlers')
export const addJob = (data: SysJob) => request.post('/job/add', data)
export const updateJob = (data: SysJob) => request.put('/job/update', data)
export const deleteJob = (id: number) => request.delete(`/job/delete/${id}`)
export const changeJobStatus = (id: number, status: number) =>
  request.put('/job/status', { id, status })
export const runJob = (id: number) => request.post(`/job/run/${id}`)
export const pageJobLogs = (params: { page: number; size: number; jobName?: string; status?: number }) =>
  request.get<unknown, PageResult<SysJobLog>>('/job/log/page', { params })
export const cleanJobLogs = () => request.delete('/job/log/clean')
