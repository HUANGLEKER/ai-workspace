/**
 * Agent 管理 API
 *
 * Agent 定义按 createBy 用户私有。
 * tools 与 mcpServers 字段在数据库中以 JSON 数组字符串存储，
 * 前端编辑时转为 string[] 操作，提交前再序列化回字符串。
 * runAgent 会将工具规格解析后发送给 FastAPI /agent/run 执行真实的工具调用循环。
 */
import request from './request'

export interface Agent {
  id?: number
  name: string
  description?: string
  systemPrompt?: string
  model?: string
  /** 工具中心工具名称列表，JSON 数组字符串，如 '["search","calc"]' */
  tools?: string
  /** MCP 服务器名称列表，JSON 数组字符串 */
  mcpServers?: string
  enabled?: number
  createTime?: string
}

export interface AgentRunResult {
  output: string
  steps: Record<string, unknown>[]
}

export const listAgents = () => request.get<unknown, Agent[]>('/agent/list')
export const addAgent = (data: Agent) => request.post('/agent/add', data)
export const updateAgent = (data: Agent) => request.put('/agent/update', data)
export const deleteAgent = (id: number) => request.delete(`/agent/delete/${id}`)
export const runAgent = (id: number, input: string) =>
  request.post<unknown, AgentRunResult>(`/agent/${id}/run`, { input })
