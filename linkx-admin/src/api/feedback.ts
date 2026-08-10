/**
 * 作者：yangleduo
 */
import { runAsyncExport } from './exportJobs'
import { get, post, put } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export interface FeedbackReplyItem {
  id: string
  feedbackId?: string
  senderType?: 'admin' | 'user'
  senderId?: string
  senderName?: string
  content?: string
  createTime?: string
}

export interface FeedbackItem {
  id: string
  userId?: string
  username?: string
  type?: string
  content?: string
  contact?: string
  status?: string
  reply?: string
  replyTime?: string
  createTime?: string
  overdue?: boolean
  assigneeId?: string
  assigneeName?: string
  assignedAt?: string
  escalated?: boolean
  escalationCount?: number
  escalatedAt?: string
  replies?: FeedbackReplyItem[]
}

export function listFeedback(params: PageQuery) {
  return get<PageResult<FeedbackItem>>('/admin/feedback', params as Record<string, unknown>)
}

export function getFeedback(id: string) {
  return get<FeedbackItem>(`/admin/feedback/${id}`)
}

export function listFeedbackReplies(id: string) {
  return get<FeedbackReplyItem[]>(`/admin/feedback/${id}/replies`)
}

export function replyFeedback(id: string, content: string) {
  return post<null>(`/admin/feedback/${id}/reply`, { content })
}

export function closeFeedback(id: string) {
  return post<null>(`/admin/feedback/${id}/close`)
}

export function reopenFeedback(id: string) {
  return post<null>(`/admin/feedback/${id}/reopen`)
}

export function assignFeedback(id: string, assigneeId: string | null) {
  return put<null>(`/admin/feedback/${id}/assign`, { assigneeId })
}

export function exportFeedback(params: PageQuery) {
  return runAsyncExport('feedback', params as Record<string, unknown>)
}
