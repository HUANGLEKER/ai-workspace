/**
 * 工作流管理 API
 *
 * 工作流定义按 createBy 用户私有，definition 字段为 LangGraph 节点/连线的 JSON 字符串。
 * runWorkflow 传入提示词字符串，后端包装为 inputs.prompt 转发 FastAPI /workflow/run 执行。
 */
import request from './request'

export interface Workflow {
  id?: number
  name: string
  description?: string
  definition?: string
  model?: string
  enabled?: number
  createTime?: string
}

export interface WorkflowRunResult {
  status: string
  outputs: Record<string, unknown>
}

export const listWorkflows = () => request.get<unknown, Workflow[]>('/workflow/list')
export const addWorkflow = (data: Workflow) => request.post('/workflow/add', data)
export const updateWorkflow = (data: Workflow) => request.put('/workflow/update', data)
export const deleteWorkflow = (id: number) => request.delete(`/workflow/delete/${id}`)
export const runWorkflow = (id: number, input: string) =>
  request.post<unknown, WorkflowRunResult>(`/workflow/${id}/run`, { input })
