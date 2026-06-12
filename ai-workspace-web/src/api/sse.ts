/**
 * 共享 SSE 流式工具
 *
 * Axios 不支持流式读取响应体，所有 SSE 端点改用原生 fetch()。
 * 本模块统一处理各调用方共有的传输层关注点：鉴权头注入、
 * 增量 UTF-8 解码、行缓冲、data: 解析、[DONE] 哨兵识别
 * 以及 AbortController 取消支持。
 * 各 API 模块只需提供 URL、请求体以及如何将载荷映射为展示文本。
 */

export interface StreamSSEOptions {
  /** 每收到一个解码后的文本片段时触发 */
  onChunk: (text: string) => void
  /** 流结束时恰好触发一次：[DONE] 哨兵、自然关闭或用户中止均会调用 */
  onDone: () => void
  /** 非中止类传输错误或 HTTP 错误时触发 */
  onError: (err: string) => void
  /** 可选的取消信号，例如"停止生成"按钮绑定的 AbortController.signal */
  signal?: AbortSignal
  /**
   * 将原始 data: 载荷映射为展示文本。
   * 返回字符串则通过 onChunk 输出，返回 undefined 则忽略该帧（如元数据帧）。
   *
   * 默认从 JSON 载荷中依次取 content / text / delta 字段，均不存在时回退为原始字符串。
   * RAG 流式场景可传入自定义提取器，例如：
   * `(d) => { const j = JSON.parse(d); return j.type === 'token' ? j.token : undefined }`
   */
  extract?: (data: string) => string | undefined
  /**
   * 非展示文本帧（extract 返回 undefined 的帧，如 token 统计、元数据）的旁路回调。
   * 用于 SSE 中夹带的结构化信息（例如 {"type":"usage",...}），与正文 token 解耦，
   * 不进入 onChunk，因此不会阻塞/污染 Markdown 渲染。
   */
  onMeta?: (data: string) => void
}

const defaultExtract = (data: string): string | undefined => {
  try {
    const parsed = JSON.parse(data)
    // token 是 FastAPI chat 流的载荷字段（{"session_id","token"}）
    return parsed.token ?? parsed.content ?? parsed.text ?? parsed.delta ?? undefined
  } catch {
    return data || undefined
  }
}

/**
 * 以 JSON 格式 POST body 到 url，并以 SSE 方式消费响应流。
 * 流结束时 Promise resolve；不会 reject，错误通过 onError 回调传递。
 */
export async function streamSSE(url: string, body: unknown, opts: StreamSSEOptions): Promise<void> {
  const { onChunk, onDone, onError, signal, extract = defaultExtract, onMeta } = opts
  const token = localStorage.getItem('token')
  let finished = false
  const finish = () => {
    if (!finished) {
      finished = true
      onDone()
    }
  }

  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: JSON.stringify(body),
      signal
    })

    if (!response.ok) {
      onError(`请求失败：${response.status}`)
      return
    }

    // 后端约定错误（如限流 429）以 HTTP 200 + JSON 包装返回，并非 SSE 流。
    // 不在此拦截的话这类响应没有任何 data: 行，会被静默当作空流结束。
    const contentType = response.headers.get('content-type') ?? ''
    if (contentType.includes('application/json')) {
      try {
        const result = await response.json()
        onError(result.message || `请求失败：${result.code ?? '未知错误'}`)
      } catch {
        onError('请求失败：响应格式异常')
      }
      return
    }

    const reader = response.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    // 返回 true 表示收到 [DONE] 哨兵，外层循环应停止读取
    const handleLine = (line: string): boolean => {
      if (!line.startsWith('data:')) return false
      // SSE 规范允许 "data:" 后跟一个可选空格，兼容两种格式
      const raw = line.slice(5)
      const data = raw.startsWith(' ') ? raw.slice(1) : raw
      if (data === '[DONE]') return true
      const text = extract(data)
      if (text) onChunk(text)
      else onMeta?.(data)
      return false
    }

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      // 末尾可能是不完整的行，暂存到 buffer 等待下一次 read 补全
      const lines = buffer.split('\n')
      buffer = lines.pop() ?? ''
      for (const line of lines) {
        if (handleLine(line)) {
          finish()
          return
        }
      }
    }
    // 流关闭后冲刷 buffer 中残留的最后一行
    if (buffer) handleLine(buffer)
    finish()
  } catch (e) {
    // 用户主动中止不视为错误，正常完结已接收到的内容
    if ((e as Error).name === 'AbortError') {
      finish()
      return
    }
    onError((e as Error).message || '连接失败')
  }
}
