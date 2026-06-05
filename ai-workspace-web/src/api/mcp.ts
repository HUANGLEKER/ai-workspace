import request from './request'

export interface McpServer {
  id?: number
  name: string
  description?: string
  transport?: string
  url?: string
  command?: string
  config?: string
  enabled?: number
  createTime?: string
}

export const listMcpServers = () => request.get<unknown, McpServer[]>('/mcp/list')
export const getMcpServer = (id: number) => request.get<unknown, McpServer>(`/mcp/${id}`)
export const addMcpServer = (data: McpServer) => request.post('/mcp/add', data)
export const updateMcpServer = (data: McpServer) => request.put('/mcp/update', data)
export const deleteMcpServer = (id: number) => request.delete(`/mcp/delete/${id}`)
export const testMcpServer = (id: number) => request.post<unknown, string>(`/mcp/test/${id}`)
