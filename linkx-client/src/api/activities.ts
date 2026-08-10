/**
 * 作者：yangleduo
 */
/**
 * 客户端运营活动 API
 */

import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface AppActivity {
  id: string
  title?: string
  coverUrl: string
  linkUrl?: string
  description?: string
  sortOrder?: number
}

export function listActivities() {
  return apiClient.get<never, ApiResult<AppActivity[]>>('/app/activities')
}
