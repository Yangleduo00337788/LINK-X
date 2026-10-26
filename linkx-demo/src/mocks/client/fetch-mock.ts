/**
 * 作者：yangleduo
 */
import { ctxFromConfig } from '../utils'
import { resolveClientMock } from './handlers'
import { appendLinkMate, nextId } from './demo-state'

function sseChunk(event: string, data: unknown) {
  return `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`
}

function toSameOrigin(url: string): string {
  return url
    .replace(/^https?:\/\/127\.0\.0\.1:8080/i, window.location.origin)
    .replace(/^https?:\/\/localhost:8080/i, window.location.origin)
}

function isBinaryMediaPath(apiPath: string): boolean {
  return (
    /\/media\//.test(apiPath) ||
    /\/chat\/messages\/[^/]+\/file$/.test(apiPath) ||
    /\/cloud\/files\/[^/]+\/content$/.test(apiPath) ||
    /\/short-video\/[^/]+\/(?:cover|video)\/content$/.test(apiPath)
  )
}

export function installClientFetchMock() {
  const native = window.fetch.bind(window)
  window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    const raw = String(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url)
    const url = toSameOrigin(raw)
    const pathOnly = url.replace(/^https?:\/\/[^/]+/i, '').split('?')[0] || ''
    const apiPath = pathOnly.startsWith('/client/api') ? pathOnly.slice('/client'.length) : pathOnly
    if (!apiPath.startsWith('/api') || isBinaryMediaPath(apiPath)) {
      return native(url, init)
    }

    if (apiPath.includes('/linkmate/chat/stream')) {
      let body: Record<string, unknown> = {}
      try {
        body = JSON.parse(String(init?.body || '{}')) as Record<string, unknown>
      } catch {
        body = {}
      }
      const sessionId = String(body.sessionId || 'lm-1')
      const question = String(body.message || '')
      appendLinkMate(sessionId, 'user', question)
      const reply = question
        ? `【演示灵伴】已收到「${question.slice(0, 40)}」。当前为静态演示站，回复由本地 MOCK 生成：本周评审确认交互稿，会议室 3F 梧桐厅，发版 checklist 已同步云盘。`
        : '【演示灵伴】你好，我是 LinkX 灵伴。演示环境中所有能力均为本地模拟。'
      appendLinkMate(sessionId, 'assistant', reply)
      const messageId = nextId('lmm-')
      const encoder = new TextEncoder()
      const stream = new ReadableStream({
        start(controller) {
          controller.enqueue(encoder.encode(sseChunk('start', { sessionId })))
          const parts = reply.match(/.{1,12}/g) || [reply]
          for (const part of parts) {
            controller.enqueue(encoder.encode(sseChunk('delta', { content: part })))
          }
          controller.enqueue(
            encoder.encode(sseChunk('done', { messageId, sessionId, totalTokens: 64 }))
          )
          controller.close()
        },
      })
      return new Response(stream, {
        status: 200,
        headers: { 'Content-Type': 'text/event-stream; charset=utf-8' },
      })
    }

    const method = (init?.method || (typeof input !== 'string' && !(input instanceof URL) ? input.method : 'GET') || 'GET').toLowerCase()
    let data: unknown
    if (init?.body) {
      try {
        data = JSON.parse(String(init.body))
      } catch {
        data = init.body
      }
    }
    const ctx = ctxFromConfig(
      {
        url: apiPath.replace(/^\/api/, '') + (url.includes('?') ? `?${url.split('?')[1]}` : ''),
        baseURL: '/api',
        method,
        data,
      } as import('axios').InternalAxiosRequestConfig,
      'client'
    )
    const payload = (await resolveClientMock(ctx)) ?? { code: 200, message: '', data: null }
    return new Response(JSON.stringify(payload), {
      status: 200,
      headers: { 'Content-Type': 'application/json; charset=utf-8' },
    })
  }
}
