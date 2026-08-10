/**
 * 作者：yangleduo
 */
import { del, get, post, put } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export interface ActivityItem {
  id: string
  title?: string
  coverUrl: string
  coverKey?: string
  linkUrl?: string | null
  description?: string | null
  sortOrder?: number
  status?: string
  startAt?: string | null
  endAt?: string | null
  publishedAt?: string
  createTime?: string
  updateTime?: string
}

export interface ActivityPayload {
  title?: string
  coverUrl: string
  linkUrl?: string | null
  description?: string | null
  sortOrder?: number
  startAt?: number | null
  endAt?: number | null
}

export interface ActivityUploadResult {
  objectKey: string
  url: string
}

export interface ActivityQuery extends PageQuery {
  activityStatus?: string
}

export function listActivities(params: ActivityQuery) {
  return get<PageResult<ActivityItem>>('/admin/activities', params as Record<string, unknown>)
}

export function uploadActivityCover(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return post<ActivityUploadResult>('/admin/activities/upload', formData)
}

export function createActivity(body: ActivityPayload) {
  return post<ActivityItem>('/admin/activities', body)
}

export function updateActivity(id: string, body: ActivityPayload) {
  return put<ActivityItem>(`/admin/activities/${id}`, body)
}

export function deleteActivity(id: string) {
  return del<null>(`/admin/activities/${id}`)
}

export function publishActivity(id: string) {
  return post<ActivityItem>(`/admin/activities/${id}/publish`)
}

export function unpublishActivity(id: string) {
  return post<ActivityItem>(`/admin/activities/${id}/unpublish`)
}
