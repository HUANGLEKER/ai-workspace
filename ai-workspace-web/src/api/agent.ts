/**
 * Agent 管理 API
 *
 * Agent 定义按 createBy 用户私有。
 * tools 与 mcpServers 字段在数据库中以 JSON 数组字符串存储，
 * 前端编辑时转为 string[] 操作，提交前再序列化回字符串。
 * runAgent 会将工具规格解析后发送给 FastAPI /agent/run 执行真实的工具调用循环。
 */
import request from './request'
import { streamSSE } from './sse'

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

/** Agent 流式运行的一步执行轨迹 */
export interface AgentStep {
  type: string
  tool?: string
  args?: unknown
  content?: string
}

/**
 * 流式运行 Agent（P2-5）：逐步接收 think→act 轨迹，复用 streamSSE。
 * onStep 每收到一步轨迹触发，onAnswer 收到最终答案触发。
 */
export function runAgentStream(
  id: number,
  input: string,
  handlers: {
    onStep: (step: AgentStep) => void
    onAnswer: (output: string) => void
    onDone: () => void
    onError: (err: string) => void
  },
  signal?: AbortSignal
): Promise<void> {
  return streamSSE(`/api/agent/${id}/run/stream`, { input }, {
    signal,
    // step/answer/error 帧不是展示文本，统一走 onMeta 分拣
    extract: () => undefined,
    onMeta: (data) => {
      try {
        const frame = JSON.parse(data)
        if (frame.type === 'step') handlers.onStep(frame.step as AgentStep)
        else if (frame.type === 'answer') handlers.onAnswer(frame.output ?? '')
        else if (frame.type === 'error') handlers.onError(frame.error ?? '运行出错')
      } catch {
        /* 忽略无法解析的帧 */
      }
    },
    onChunk: () => {},
    onDone: handlers.onDone,
    onError: handlers.onError
  })
}
