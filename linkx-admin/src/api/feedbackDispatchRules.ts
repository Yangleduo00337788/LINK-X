import { del, get, post, put } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export interface FeedbackDispatchRuleItem {
  id: string
  name: string
  feedbackType?: string
  keyword?: string
  assigneeId?: string
  assigneeName?: string
  priority?: number
  enabled?: boolean
  createTime?: string
  updateTime?: string
}

export interface FeedbackDispatchRulePayload {
  name: string
  feedbackType?: string
  keyword?: string
  assigneeId: string
  priority?: number
  enabled?: boolean
}

export function listFeedbackDispatchRules(params: PageQuery) {
  return get<PageResult<FeedbackDispatchRuleItem>>(
    '/admin/feedback-dispatch-rules',
    params as Record<string, unknown>
  )
}

export function createFeedbackDispatchRule(body: FeedbackDispatchRulePayload) {
  return post<FeedbackDispatchRuleItem>('/admin/feedback-dispatch-rules', body)
}

export function updateFeedbackDispatchRule(id: string, body: FeedbackDispatchRulePayload) {
  return put<FeedbackDispatchRuleItem>(`/admin/feedback-dispatch-rules/${id}`, body)
}

export function deleteFeedbackDispatchRule(id: string) {
  return del<null>(`/admin/feedback-dispatch-rules/${id}`)
}
