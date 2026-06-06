/**
 * Shared Server-Sent-Events (SSE) streaming helper.
 *
 * Axios cannot read a streaming body, so all SSE endpoints use raw `fetch()`.
 * This centralizes the transport concerns every SSE caller needs — auth header,
 * incremental UTF-8 decoding, line buffering, `data: ` parsing, the `[DONE]`
 * sentinel, and AbortController support — so individual API modules only supply
 * the URL, body and how to turn a payload into display text.
 */

export interface StreamSSEOptions {
  /** Called for each decoded text chunk. */
  onChunk: (text: string) => void
  /** Called exactly once when the stream finishes: DONE sentinel, natural close, or user abort. */
  onDone: () => void
  /** Called on a non-abort transport/HTTP error. */
  onError: (err: string) => void
  /** Optional signal to cancel the stream (e.g. a "stop generating" button). */
  signal?: AbortSignal
  /**
   * Map a raw `data:` payload to display text. Return a string to emit via
   * {@link onChunk}, or `undefined` to ignore the event (e.g. metadata frames).
   *
   * Defaults to extracting `content` / `text` / `delta` from a JSON payload and
   * falling back to the raw string. For the RAG stream, pass a custom extractor:
   * `(d) => { const j = JSON.parse(d); return j.type === 'token' ? j.token : undefined }`.
   */
  extract?: (data: string) => string | undefined
}

const defaultExtract = (data: string): string | undefined => {
  try {
    const parsed = JSON.parse(data)
    return parsed.content ?? parsed.text ?? parsed.delta ?? undefined
  } catch {
    return data || undefined
  }
}

/**
 * POST {@code body} as JSON to {@code url} and stream the SSE response.
 * Resolves when the stream ends; never rejects (errors are delivered via callbacks).
 */
export async function streamSSE(url: string, body: unknown, opts: StreamSSEOptions): Promise<void> {
  const { onChunk, onDone, onError, signal, extract = defaultExtract } = opts
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

    const reader = response.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    // Returns true when the [DONE] sentinel was seen and streaming should stop.
    const handleLine = (line: string): boolean => {
      if (!line.startsWith('data: ')) return false
      const data = line.slice(6).trim()
      if (data === '[DONE]') return true
      const text = extract(data)
      if (text) onChunk(text)
      return false
    }

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      // Keep the last (possibly incomplete) line buffered until the next read.
      const lines = buffer.split('\n')
      buffer = lines.pop() ?? ''
      for (const line of lines) {
        if (handleLine(line)) {
          finish()
          return
        }
      }
    }
    // Flush any remaining buffered line after the stream closes.
    if (buffer) handleLine(buffer)
    finish()
  } catch (e) {
    // A user-initiated abort is not an error — finalize whatever was streamed.
    if ((e as Error).name === 'AbortError') {
      finish()
      return
    }
    onError((e as Error).message || '连接失败')
  }
}
