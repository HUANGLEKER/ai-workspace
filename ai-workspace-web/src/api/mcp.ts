/**
 * MCP 服务器注册表 API
 *
 * MCP 服务器按 createBy 用户私有，支持 sse/stdio 两种传输方式。
 * testMcpServer 仅对 sse 类型做 HTTP 可达性探测，返回连接状态字符串。
 * Agent 运行时会通过 langchain-mcp-adapters 动态加载 SSE 类型服务器的工具列表。
 */
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
export const addMcpServer = (data: McpServer) => request.post('/mcp/add', data)
export const updateMcpServer = (data: McpServer) => request.put('/mcp/update', data)
export const deleteMcpServer = (id: number) => request.delete(`/mcp/delete/${id}`)
export const testMcpServer = (id: number) => request.post<unknown, string>(`/mcp/test/${id}`)
