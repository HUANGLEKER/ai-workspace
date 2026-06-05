import request from './request'

export interface Agent {
  id?: number
  name: string
  description?: string
  systemPrompt?: string
  model?: string
  /** JSON array string of Tool Center tool names */
  tools?: string
  /** JSON array string of MCP server names */
  mcpServers?: string
  enabled?: number
  createTime?: string
}

export interface AgentRunResult {
  output: string
  steps: Record<string, unknown>[]
}

export const listAgents = () => request.get<unknown, Agent[]>('/agent/list')
export const getAgent = (id: number) => request.get<unknown, Agent>(`/agent/${id}`)
export const addAgent = (data: Agent) => request.post('/agent/add', data)
export const updateAgent = (data: Agent) => request.put('/agent/update', data)
export const deleteAgent = (id: number) => request.delete(`/agent/delete/${id}`)
export const runAgent = (id: number, input: string) =>
  request.post<unknown, AgentRunResult>(`/agent/${id}/run`, { input })
