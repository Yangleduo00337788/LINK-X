/**
 * 作者：yangleduo
 */
import { del, get, post } from './request'

export type RateLimitHit = {
  redisKey: string
  scope?: string
  ip?: string
  identity?: string
  count: number
  ttlSeconds?: number | null
}

export function listRateLimitHits(params?: { ip?: string; limit?: number }) {
  return get<RateLimitHit[]>('/admin/rate-limits/hits', (params || {}) as Record<string, unknown>)
}

export function unblockRateLimitIp(ip: string) {
  return post<{ ip: string; deleted: number }>('/admin/rate-limits/unblock', { ip })
}

export function listRateLimitWhitelist() {
  return get<string[]>('/admin/rate-limits/whitelist')
}

export function addRateLimitWhitelist(ip: string) {
  return post<void>('/admin/rate-limits/whitelist', { ip })
}

export function removeRateLimitWhitelist(ip: string) {
  return del<void>(`/admin/rate-limits/whitelist?ip=${encodeURIComponent(ip)}`)
}
