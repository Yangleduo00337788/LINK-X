/**
 * 作者：yangleduo
 */
import { del, get, post, put } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export type RecommendSlot = 'discover' | 'chat_sidebar' | 'moments'

export interface RecommendItem {
  id: string
  slotCode?: RecommendSlot | string
  title?: string
  subtitle?: string
  imageUrl: string
  imageKey?: string
  linkUrl?: string | null
  sortOrder?: number
  status?: string
  startAt?: string | null
  endAt?: string | null
  publishedAt?: string
  createTime?: string
  updateTime?: string
}

export interface RecommendPayload {
  slotCode: RecommendSlot | string
  title?: string
  subtitle?: string | null
  imageUrl: string
  linkUrl?: string | null
  sortOrder?: number
  startAt?: number | null
  endAt?: number | null
}

export interface RecommendUploadResult {
  objectKey: string
  url: string
}

export interface RecommendQuery extends PageQuery {
  recommendStatus?: string
  slotCode?: RecommendSlot | ''
}

export function listRecommends(params: RecommendQuery) {
  return get<PageResult<RecommendItem>>('/admin/recommends', params as Record<string, unknown>)
}

export function uploadRecommendImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return post<RecommendUploadResult>('/admin/recommends/upload', formData)
}

export function createRecommend(body: RecommendPayload) {
  return post<RecommendItem>('/admin/recommends', body)
}

export function updateRecommend(id: string, body: RecommendPayload) {
  return put<RecommendItem>(`/admin/recommends/${id}`, body)
}

export function deleteRecommend(id: string) {
  return del<null>(`/admin/recommends/${id}`)
}

export function publishRecommend(id: string) {
  return post<RecommendItem>(`/admin/recommends/${id}/publish`)
}

export function unpublishRecommend(id: string) {
  return post<RecommendItem>(`/admin/recommends/${id}/unpublish`)
}
