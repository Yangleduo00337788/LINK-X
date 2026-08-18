/**
 * 作者：yangleduo
 */
export type LinkMateSsePayload = Record<string, unknown>

export interface LinkMateSseHandlers {
  onStart?: (payload: LinkMateSsePayload) => void
  onDelta?: (content: string) => void
  onReasoningDelta?: (content: string) => void
  onDone?: (payload: LinkMateSsePayload) => void
  onError?: (message: string) => void
}

function parseSsePayload(raw: string): { eventName: string; payload: LinkMateSsePayload } | null {
  let eventName = 'message'
  let data = ''
  for (const line of raw.split('\n')) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      const piece = line.slice(5).trimStart()
      data += data ? '\n' + piece : piece
    }
  }
  if (!data) return null

  try {
    let payload = JSON.parse(data) as LinkMateSsePayload
    if (typeof payload === 'string') {
      payload = JSON.parse(payload) as LinkMateSsePayload
    }
    return { eventName, payload }
  } catch {
    return null
  }
}

export function dispatchLinkMateSseEvent(eventName: string, payload: LinkMateSsePayload, handlers: LinkMateSseHandlers) {
  switch (eventName) {
    case 'start':
      handlers.onStart?.(payload)
      break
    case 'delta':
      if (typeof payload.content === 'string' && payload.content !== '') handlers.onDelta?.(payload.content)
      break
    case 'reasoning_delta':
      if (typeof payload.content === 'string' && payload.content !== '')
        handlers.onReasoningDelta?.(payload.content)
      break
    case 'done':
      handlers.onDone?.(payload)
      break
    case 'error':
      handlers.onError?.(
        (typeof payload.message === 'string' && payload.message) || 'AI 服务错误'
      )
      break
    default:
      break
  }
}

export function parseLinkMateSseEvent(raw: string, handlers: LinkMateSseHandlers) {
  const parsed = parseSsePayload(raw)
  if (!parsed) return
  dispatchLinkMateSseEvent(parsed.eventName, parsed.payload, handlers)
}

/** 读取 SSE 响应体并分发事件 */
export async function readLinkMateSseStream(
  body: ReadableStream<Uint8Array> | null,
  handlers: LinkMateSseHandlers
): Promise<void> {
  const reader = body?.getReader()
  if (!reader) {
    handlers.onError?.('无法读取响应流')
    return
  }

  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    buffer = buffer.replace(/\r\n/g, '\n')

    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      const rawEvent = buffer.slice(0, boundary)
      buffer = buffer.slice(boundary + 2)
      parseLinkMateSseEvent(rawEvent, handlers)
      boundary = buffer.indexOf('\n\n')
    }
  }

  if (buffer.trim()) {
    parseLinkMateSseEvent(buffer.replace(/\r\n/g, '\n'), handlers)
  }
}
