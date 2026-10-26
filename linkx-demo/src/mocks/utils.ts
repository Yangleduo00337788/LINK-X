/**
 * 作者：yangleduo
 */
import type { InternalAxiosRequestConfig } from 'axios'
import type { MockContext } from './types'

export function normalizePath(url: string): string {
  let path = url.split('?')[0] || '/'
  path = path.replace(/^https?:\/\/[^/]+/i, '')
  if (path.startsWith('/api')) path = path.slice(4) || '/'
  if (!path.startsWith('/')) path = `/${path}`
  return path.replace(/\/+$/, '') || '/'
}

export function parseParams(url: string): Record<string, unknown> {
  const q = url.includes('?') ? url.split('?')[1] : ''
  const out: Record<string, unknown> = {}
  if (!q) return out
  for (const part of q.split('&')) {
    const [k, v] = part.split('=')
    if (!k) continue
    out[decodeURIComponent(k)] = decodeURIComponent(v || '')
  }
  return out
}

export function ctxFromConfig(config: InternalAxiosRequestConfig, app: 'admin' | 'client'): MockContext {
  const rawUrl = config.url || '/'
  const base = (config.baseURL || '').replace(/\/$/, '')
  const full = rawUrl.startsWith('http') ? rawUrl : `${base}${rawUrl.startsWith('/') ? '' : '/'}${rawUrl}`
  const method = (config.method || 'get').toLowerCase()
  const path = normalizePath(full)
  const params = {
    ...parseParams(full),
    ...(config.params as Record<string, unknown> | undefined),
  }
  let data: unknown = config.data
  if (typeof data === 'string' && data.trim().startsWith('{')) {
    try {
      data = JSON.parse(data)
    } catch {
      /* keep string */
    }
  }
  return { app, method, url: full, path, params, data }
}

export function trendLabels(days: number): string[] {
  const n = Math.max(7, Math.min(30, Number(days) || 14))
  const labels: string[] = []
  const now = new Date()
  for (let i = n - 1; i >= 0; i--) {
    const d = new Date(now)
    d.setDate(d.getDate() - i)
    labels.push(`${d.getMonth() + 1}/${d.getDate()}`)
  }
  return labels
}
