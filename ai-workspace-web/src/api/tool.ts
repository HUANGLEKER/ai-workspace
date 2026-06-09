/**
 * 工具中心 API
 *
 * 工具按 createBy 用户私有。toolType 区分 http（Agent 运行时通过 httpx 调用）
 * 与 builtin（内置逻辑）。config 字段存储 JSON 格式的参数 Schema。
 */
import request from './request'

export interface Tool {
  id?: number
  name: string
  description?: string
  toolType?: string
  endpoint?: string
  config?: string
  enabled?: number
  createTime?: string
}

export const listTools = () => request.get<unknown, Tool[]>('/tool/list')
export const addTool = (data: Tool) => request.post('/tool/add', data)
export const updateTool = (data: Tool) => request.put('/tool/update', data)
export const deleteTool = (id: number) => request.delete(`/tool/delete/${id}`)
