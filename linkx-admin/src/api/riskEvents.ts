import { runAsyncExport } from './exportJobs'
import { get, post } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export interface RiskEventItem {
  id: string
  eventType?: string
  title?: string
  detail?: string
  riskLevel?: string
  status?: string
  userId?: string
  username?: string
  targetResourceId?: string
  targetResourceType?: string
  ip?: string
  region?: string
  extraData?: string
  auditLogId?: string
  resolution?: string
  handledBy?: string
  handledAt?: string
  createTime?: string
}

export interface RiskEventQuery extends PageQuery {
  eventStatus?: string
  eventType?: string
  riskLevel?: string
}

export function listRiskEvents(params: RiskEventQuery) {
  return get<PageResult<RiskEventItem>>('/admin/risk-events', params as Record<string, unknown>)
}

export function getRiskEvent(id: string) {
  return get<RiskEventItem>(`/admin/risk-events/${id}`)
}

export function handleRiskEvent(
  id: string,
  action: 'handled' | 'ignored',
  resolution?: string,
  userAction?: 'none' | 'freeze' | 'ban'
) {
  return post<null>(`/admin/risk-events/${id}/handle`, {
    action,
    resolution,
    userAction: userAction || 'none',
  })
}

export interface RiskBatchResult {
  successCount: number
  failCount: number
  failures?: { id: string; reason?: string }[]
}

export function batchRiskEvents(
  ids: Array<string | number>,
  action: 'handled' | 'ignored',
  resolution?: string
) {
  return post<RiskBatchResult>('/admin/risk-events/batch', {
    ids,
    action,
    resolution,
  })
}

export function exportRiskEvents(params: RiskEventQuery) {
  return runAsyncExport('risk-events', params as Record<string, unknown>)
}
