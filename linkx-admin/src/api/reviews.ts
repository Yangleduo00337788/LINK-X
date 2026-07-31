import { get, post } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export interface ReviewItem {
  id: string
  sourceType?: string
  targetType?: string
  targetId?: string
  reporterUserId?: string
  reporterUsername?: string
  title?: string
  contentSnapshot?: string
  /** 证据图片可访问 URL */
  evidenceUrls?: string[]
  riskLevel?: string
  status?: string
  feedbackId?: string
  resolution?: string
  resolvedBy?: string
  resolvedAt?: string
  createTime?: string
}

export interface ReviewQuery extends PageQuery {
  reviewStatus?: string
  sourceType?: string
}

export function listReviews(params: ReviewQuery) {
  return get<PageResult<ReviewItem>>('/admin/reviews', params as Record<string, unknown>)
}

export function getReview(id: string) {
  return get<ReviewItem>(`/admin/reviews/${id}`)
}

export function approveReview(id: string, resolution?: string) {
  return post<null>(`/admin/reviews/${id}/approve`, { resolution })
}

export function rejectReview(id: string, resolution?: string) {
  return post<null>(`/admin/reviews/${id}/reject`, { resolution })
}
