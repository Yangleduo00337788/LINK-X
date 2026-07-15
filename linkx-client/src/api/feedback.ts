/**
 * 帮助与反馈 API
 */

import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface FeedbackPayload {
  type: 'bug' | 'suggestion' | 'other'
  content: string
  contact?: string
}

export interface FeedbackVO {
  id: string
  type: string
  content: string
  status: 'pending' | 'processing' | 'resolved'
  createTime: string
}

/**
 * 提交反馈
 */
export function submitFeedback(payload: FeedbackPayload) {
  return apiClient.post<never, ApiResult<FeedbackVO>>('/feedback', payload)
}

/**
 * 获取我的反馈列表
 */
export function listFeedback() {
  return apiClient.get<never, ApiResult<FeedbackVO[]>>('/feedback')
}
