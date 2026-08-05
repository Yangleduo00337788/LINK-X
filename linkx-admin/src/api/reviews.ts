import { runAsyncExport } from './exportJobs'
import { get, post } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export interface ReviewRiskEventBrief {
  id?: string
  eventType?: string
  title?: string
  riskLevel?: string
  status?: string
  createTime?: string
}

export interface ReviewRiskContext {
  riskScore?: number
  computedRiskLevel?: string
  riskFactors?: string[]
  recentRiskEventCount24h?: number
  recentHighRiskCount24h?: number
  recentRiskEvents?: ReviewRiskEventBrief[]
}

export interface ReviewItem {
  id: string
  sourceType?: string
  targetType?: string
  targetId?: string
  /** 涉事用户 ID */
  subjectUserId?: string
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
  overdue?: boolean
  escalated?: boolean
  escalationCount?: number
  escalatedAt?: string
  riskContext?: ReviewRiskContext
  /** 关联审批实例 ID */
  approvalInstanceId?: string | number
  /** 审批状态 pending|approved|rejected */
  approvalStatus?: string
}

export type ReviewUserAction = 'none' | 'freeze' | 'ban'
export type ReviewContentAction = 'none' | 'delete'
export type ReviewGroupAction = 'none' | 'dissolve' | 'freeze_owner' | 'ban_owner'

export interface ReviewResolvePayload {
  resolution?: string
  userAction?: ReviewUserAction
  contentAction?: ReviewContentAction
  groupAction?: ReviewGroupAction
}

export interface ReviewQuery extends PageQuery {
  reviewStatus?: string
  sourceType?: string
  targetType?: string
  riskLevel?: string
  overdueOnly?: boolean
  escalatedOnly?: boolean
}

export interface ReviewBatchResult {
  successCount: number
  failCount: number
  failures?: { id: string; reason?: string }[]
}

export function listReviews(params: ReviewQuery) {
  return get<PageResult<ReviewItem>>('/admin/reviews', params as Record<string, unknown>)
}

export function getReview(id: string) {
  return get<ReviewItem>(`/admin/reviews/${id}`)
}

export function approveReview(id: string, payload?: ReviewResolvePayload | string) {
  const body =
    typeof payload === 'string' || payload === undefined ? { resolution: payload } : payload
  return post<null>(`/admin/reviews/${id}/approve`, body)
}

export function rejectReview(id: string, payload?: ReviewResolvePayload | string) {
  const body =
    typeof payload === 'string' || payload === undefined ? { resolution: payload } : payload
  return post<null>(`/admin/reviews/${id}/reject`, body)
}

export function deleteReviewContent(
  id: string,
  payload?: Pick<ReviewResolvePayload, 'resolution'>
) {
  return post<null>(`/admin/reviews/${id}/delete-content`, payload ?? {})
}

export function batchReviews(
  ids: string[],
  action: 'approve' | 'reject',
  resolution?: string,
  extra?: Pick<ReviewResolvePayload, 'userAction' | 'contentAction'>
) {
  return post<ReviewBatchResult>('/admin/reviews/batch', {
    ids,
    action,
    resolution,
    ...(action === 'approve' ? extra : {}),
  })
}

export function exportReviews(params: ReviewQuery) {
  return runAsyncExport('reviews', params as Record<string, unknown>)
}
