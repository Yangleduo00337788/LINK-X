import { downloadFile, get } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export interface AuditLog {
  id: number
  operationType?: string
  description?: string
  userId?: number
  username?: string
  targetUserId?: number
  targetUsername?: string
  targetResourceId?: string
  targetResourceType?: string
  ip?: string
  userAgent?: string
  status?: string
  failureReason?: string
  extraData?: string
  createTime?: string
}

export interface LoginLog {
  id: number
  userId?: number
  username?: string
  ip?: string
  userAgent?: string
  success?: number
  reason?: string
  createTime?: string
}

export function listAuditLogs(params: PageQuery) {
  return get<PageResult<AuditLog>>('/admin/audit-logs', params as Record<string, unknown>)
}

export function exportAuditLogs(params: PageQuery) {
  return downloadFile('/admin/audit-logs/export', params as Record<string, unknown>, 'audit-logs.csv')
}

export function listLoginLogs(params: PageQuery) {
  return get<PageResult<LoginLog>>('/admin/login-logs', params as Record<string, unknown>)
}

export function exportLoginLogs(params: PageQuery) {
  return downloadFile('/admin/login-logs/export', params as Record<string, unknown>, 'login-logs.csv')
}
