/**
 * 作者：yangleduo
 */
/**
 * 客户端运营推荐位 API
 */

import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export type RecommendSlot = 'discover' | 'chat_sidebar' | 'moments'

export interface AppRecommend {
  id: string
  slotCode: string
  title?: string
  subtitle?: string
  imageUrl: string
  linkUrl?: string
  sortOrder?: number
}

export function listRecommends(slotCode?: RecommendSlot) {
  return apiClient.get<never, ApiResult<AppRecommend[]>>('/app/recommends', {
    params: slotCode ? { slotCode } : undefined
  })
}
