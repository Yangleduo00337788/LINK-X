/**
 * 作者：yangleduo
 */
import { downloadFile, get } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export interface AbnormalAccessItem {
  source?: string
  sourceId?: string
  category?: string
  title?: string
  detail?: string
  ip?: string
  region?: string
  username?: string
  identity?: string
  hitCount?: number
  ttlSeconds?: number
  riskLevel?: string
  status?: string
  occurredAt?: string
}

export interface AbnormalAccessSummary {
  loginFail24h: number
  rateLimitActive: number
  riskEventPending: number
}

export interface AbnormalAccessQuery extends PageQuery {
  source?: string
  ip?: string
}

export function getAbnormalAccessSummary() {
  return get<AbnormalAccessSummary>('/admin/abnormal-access/summary')
}

export function listAbnormalAccess(params: AbnormalAccessQuery) {
  return get<PageResult<AbnormalAccessItem>>(
    '/admin/abnormal-access',
    params as Record<string, unknown>
  )
}

export function exportAbnormalAccess(params: AbnormalAccessQuery) {
  return downloadFile(
    '/admin/abnormal-access/export',
    params as Record<string, unknown>,
    'abnormal-access.csv'
  )
}
