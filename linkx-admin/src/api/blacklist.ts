import { downloadFile, get, post } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export interface BlacklistItem {
  id: string
  userId?: string
  username?: string
  nickname?: string
  reason?: string
  status?: string
  createdBy?: string
  createdByName?: string
  releasedBy?: string
  releasedByName?: string
  releasedAt?: string
  releaseReason?: string
  createTime?: string
}

export interface BlacklistQuery extends PageQuery {
  entryStatus?: string
}

export function listBlacklist(params: BlacklistQuery) {
  return get<PageResult<BlacklistItem>>('/admin/blacklist', params as Record<string, unknown>)
}

export function getBlacklist(id: string) {
  return get<BlacklistItem>(`/admin/blacklist/${id}`)
}

export function addBlacklist(userId: string, reason?: string) {
  return post<null>('/admin/blacklist', { userId, reason })
}

export function releaseBlacklist(id: string, reason?: string) {
  return post<null>(`/admin/blacklist/${id}/release`, { reason })
}

export function exportBlacklist(params: BlacklistQuery) {
  return downloadFile('/admin/blacklist/export', params as Record<string, unknown>, 'blacklist.csv')
}
