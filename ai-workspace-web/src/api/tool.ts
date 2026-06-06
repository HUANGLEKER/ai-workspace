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
