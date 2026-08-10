/**
 * 作者：yangleduo
 */
import { del, get, post, put } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export interface VersionItem {
  id: string
  version: string
  channel: string
  releaseNotes?: string
  downloadUrl?: string
  forceUpdate?: boolean
  minSupportedVersion?: string
  status?: string
  publishedAt?: string
  publishedBy?: string
  createdBy?: string
  updatedBy?: string
  createTime?: string
  updateTime?: string
}

export interface VersionPayload {
  version: string
  channel: string
  releaseNotes?: string
  downloadUrl?: string
  forceUpdate: boolean
  minSupportedVersion?: string
}

export interface VersionQuery extends PageQuery {
  versionStatus?: string
  channel?: string
}

export function listVersions(params: VersionQuery) {
  return get<PageResult<VersionItem>>('/admin/versions', params as Record<string, unknown>)
}

export function getVersion(id: string) {
  return get<VersionItem>(`/admin/versions/${id}`)
}

export function createVersion(payload: VersionPayload) {
  return post<VersionItem>('/admin/versions', payload)
}

export function updateVersion(id: string, payload: VersionPayload) {
  return put<VersionItem>(`/admin/versions/${id}`, payload)
}

export function deleteVersion(id: string) {
  return del<void>(`/admin/versions/${id}`)
}

export function publishVersion(id: string) {
  return post<VersionItem>(`/admin/versions/${id}/publish`)
}
