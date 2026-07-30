import { get, post } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export interface FeedbackItem {
  id: string
  userId?: string
  username?: string
  type?: string
  content?: string
  contact?: string
  status?: string
  reply?: string
  createTime?: string
}

export function listFeedback(params: PageQuery) {
  return get<PageResult<FeedbackItem>>('/admin/feedback', params as Record<string, unknown>)
}

export function getFeedback(id: string) {
  return get<FeedbackItem>(`/admin/feedback/${id}`)
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
