/**
 * 作者：yangleduo
 */
import { del, get, post, put } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export interface FeedbackDispatchRuleItem {
  id: string
  name: string
  feedbackType?: string
  keyword?: string
  conditionJson?: string
  assigneeId?: string
  assigneeName?: string
  assigneeSource?: string
  dutyScheduleId?: string
  dutyScheduleName?: string
  actionType?: string
  actionConfig?: string
  notifyRoles?: string
  notifyChannels?: string
  priority?: number
  enabled?: boolean
  createTime?: string
  updateTime?: string
}

export interface FeedbackDispatchRulePayload {
  name: string
  feedbackType?: string
  keyword?: string
  conditionJson?: string
  assigneeId?: string
  assigneeSource?: string
  dutyScheduleId?: string
  actionType?: string
  actionConfig?: string
  notifyRoles?: string
  notifyChannels?: string
  priority?: number
  enabled?: boolean
}

export interface FeedbackDispatchSimulatePayload {
  type?: string
  content?: string
  status?: string
  hasAssignee?: boolean
  createOffsetHours?: number
}

export interface FeedbackDispatchSimulateResult {
  matched: boolean
  ruleId?: string
  ruleName?: string
  assigneeId?: string
  assigneeName?: string
  actionType?: string
  assigneeSource?: string
  notifyRoles?: string
  notifyChannels?: string
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

export function simulateFeedbackDispatchRule(body: FeedbackDispatchSimulatePayload) {
  return post<FeedbackDispatchSimulateResult>('/admin/feedback-dispatch-rules/simulate', body)
}
