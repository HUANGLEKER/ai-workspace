import request from './request'

export interface SysJob {
  id?: number
  jobName: string
  jobGroup?: string
  invokeTarget: string
  cronExpression: string
  jobParams?: string
  status?: number // 0 running, 1 paused
  remark?: string
  createTime?: string
}

export interface SysJobLog {
  id: number
  jobId: number
  jobName: string
  invokeTarget: string
  jobParams?: string
  status: number // 0 success, 1 fail
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
