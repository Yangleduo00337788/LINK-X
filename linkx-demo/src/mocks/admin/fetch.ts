/**
 * 作者：yangleduo
 */
import { ctxFromConfig } from '../utils'
import { resolveAdminMock } from './handlers'
import { ok } from '../types'

/** 管理端 SSE / bootstrap / 未走 axios 的 fetch 全部落到 MOCK */
export function installAdminFetchMocks() {
  const original = window.fetch.bind(window)
  window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
    const pathOnly = url.replace(/^https?:\/\/[^/]+/i, '')
    const apiPath = pathOnly.startsWith('/admin/api') ? pathOnly.slice('/admin'.length) : pathOnly

    if (url.includes('/admin/events/stream') || apiPath.includes('/admin/events/stream')) {
      const body = new ReadableStream<Uint8Array>({
        start(controller) {
          const enc = new TextEncoder()
          controller.enqueue(enc.encode(': demo stream\n\n'))
        },
      })
      return new Response(body, {
        status: 200,
        headers: {
          'Content-Type': 'text/event-stream',
          'Cache-Control': 'no-cache',
        },
      })
    }

    const isBinaryMedia =
      /^\/(?:api\/)?media\//.test(apiPath) ||
      /\/chat\/messages\/[^/]+\/file$/.test(apiPath) ||
      /\/cloud\/files\/[^/]+\/content$/.test(apiPath)
    if (!apiPath.startsWith('/api') || isBinaryMedia) {
      return original(input, init)
    }

    const method = (init?.method || 'GET').toLowerCase()
    let data: unknown
    if (init?.body && typeof init.body === 'string') {
      try {
        data = JSON.parse(init.body)
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
      'admin'
    )
    const payload = (await resolveAdminMock(ctx)) ?? ok(null)
    return new Response(JSON.stringify(payload), {
      status: 200,
      headers: { 'Content-Type': 'application/json; charset=utf-8' },
    })
  }
}
