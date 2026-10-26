/**
 * 作者：yangleduo
 */
export interface ApiEnvelope<T = unknown> {
  code: number
  message: string
  data: T
}

export type DemoApp = 'admin' | 'client'

export interface MockContext {
  app: DemoApp
  method: string
  url: string
  path: string
  params?: Record<string, unknown>
  data?: unknown
}

export type MockHandler = (ctx: MockContext) => unknown | Promise<unknown>

export function ok<T>(data: T, message = ''): ApiEnvelope<T> {
  return { code: 200, message, data }
}

export function page<T>(items: T[], page = 1, size = 20): ApiEnvelope<{ items: T[]; page: number; size: number; total: number }> {
  const p = Number(page) || 1
  const s = Number(size) || 20
  const start = (p - 1) * s
  return ok({
    items: items.slice(start, start + s),
    page: p,
    size: s,
    total: items.length,
  })
}
