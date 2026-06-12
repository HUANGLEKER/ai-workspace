/**
 * streamSSE 是所有流式端点共用的传输层，这些测试守住：
 * 行缓冲跨 chunk 拼接、UTF-8 多字节截断、[DONE] 哨兵、
 * 元数据帧旁路、JSON 错误响应兜底（限流等）、AbortSignal 取消语义。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { streamSSE } from '../sse'

/** 把若干 Uint8Array 块包装成 fetch Response（SSE content-type） */
function sseResponse(chunks: Uint8Array[]): Response {
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const c of chunks) controller.enqueue(c)
      controller.close()
    }
  })
  return new Response(stream, { headers: { 'content-type': 'text/event-stream' } })
}

function enc(s: string): Uint8Array {
  return new TextEncoder().encode(s)
}

interface Collected {
  chunks: string[]
  metas: string[]
  done: number
  errors: string[]
}

async function run(resp: Response, extra: Partial<Parameters<typeof streamSSE>[2]> = {}): Promise<Collected> {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(resp))
  const out: Collected = { chunks: [], metas: [], done: 0, errors: [] }
  await streamSSE('/api/test', {}, {
    onChunk: (t) => out.chunks.push(t),
    onMeta: (d) => out.metas.push(d),
    onDone: () => out.done++,
    onError: (e) => out.errors.push(e),
    ...extra
  })
  return out
}

beforeEach(() => {
  localStorage.setItem('token', 'tk')
})
afterEach(() => {
  vi.unstubAllGlobals()
})

describe('streamSSE 流解析', () => {
  it('解析 token 帧并以 [DONE] 收尾，onDone 恰好一次', async () => {
    const out = await run(sseResponse([
      enc('data: {"token":"he"}\n\ndata: {"token":"llo"}\n\n'),
      enc('data: [DONE]\n\n')
    ]))
    expect(out.chunks).toEqual(['he', 'llo'])
    expect(out.done).toBe(1)
    expect(out.errors).toEqual([])
  })

  it('一行被拆进两个 chunk 时正确缓冲拼接', async () => {
    const out = await run(sseResponse([
      enc('data: {"tok'),
      enc('en":"abc"}\n\ndata: [DONE]\n\n')
    ]))
    expect(out.chunks).toEqual(['abc'])
  })

  it('UTF-8 多字节字符在 chunk 边界被截断时不乱码', async () => {
    const bytes = enc('data: {"token":"中文"}\n\ndata: [DONE]\n\n')
    // 故意在「中」的 3 字节序列中间切断
    const cut = bytes.indexOf(0xe4) + 1
    const out = await run(sseResponse([bytes.slice(0, cut), bytes.slice(cut)]))
    expect(out.chunks).toEqual(['中文'])
  })

  it('extract 返回 undefined 的帧走 onMeta 旁路，不进 onChunk', async () => {
    const out = await run(sseResponse([
      enc('data: {"type":"usage","total_tokens":5}\n\ndata: {"token":"x"}\n\ndata: [DONE]\n\n')
    ]))
    expect(out.chunks).toEqual(['x'])
    expect(out.metas).toEqual(['{"type":"usage","total_tokens":5}'])
  })

  it('流自然关闭（无 [DONE]）也触发 onDone，且冲刷残留缓冲', async () => {
    const out = await run(sseResponse([enc('data: {"token":"tail"}')]))
    expect(out.chunks).toEqual(['tail'])
    expect(out.done).toBe(1)
  })

  it('注入 Authorization 头', async () => {
    const f = vi.fn().mockResolvedValue(sseResponse([enc('data: [DONE]\n\n')]))
    vi.stubGlobal('fetch', f)
    await streamSSE('/api/test', { a: 1 }, { onChunk: () => {}, onDone: () => {}, onError: () => {} })
    const headers = f.mock.calls[0][1].headers as Record<string, string>
    expect(headers['Authorization']).toBe('Bearer tk')
  })
})

describe('streamSSE 错误与取消', () => {
  it('HTTP 非 2xx 走 onError', async () => {
    const out = await run(new Response('nope', { status: 502 }))
    expect(out.errors[0]).toContain('502')
    expect(out.done).toBe(0)
  })

  it('HTTP 200 + JSON 包装错误（如限流 429）透出 message 而非静默空流', async () => {
    const resp = new Response(JSON.stringify({ code: 429, message: '请求过于频繁，请稍后再试' }), {
      headers: { 'content-type': 'application/json' }
    })
    const out = await run(resp)
    expect(out.errors).toEqual(['请求过于频繁，请稍后再试'])
    expect(out.chunks).toEqual([])
    expect(out.done).toBe(0)
  })

  it('AbortError 视为正常完结而非错误', async () => {
    const abortErr = new Error('aborted')
    abortErr.name = 'AbortError'
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(abortErr))
    const out: Collected = { chunks: [], metas: [], done: 0, errors: [] }
    await streamSSE('/api/test', {}, {
      onChunk: (t) => out.chunks.push(t),
      onDone: () => out.done++,
      onError: (e) => out.errors.push(e)
    })
    expect(out.done).toBe(1)
    expect(out.errors).toEqual([])
  })

  it('网络错误走 onError', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('connection refused')))
    const out: Collected = { chunks: [], metas: [], done: 0, errors: [] }
    await streamSSE('/api/test', {}, {
      onChunk: () => {},
      onDone: () => out.done++,
      onError: (e) => out.errors.push(e)
    })
    expect(out.errors).toEqual(['connection refused'])
    expect(out.done).toBe(0)
  })
})
