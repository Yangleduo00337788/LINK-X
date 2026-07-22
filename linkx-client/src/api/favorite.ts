/**
 * 收藏 API（独立 /favorites，与笔记拆分）
 */

import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface FavoriteVO {
  id: string
  title: string
  content: string
  type?: string
  sourceType?: string
  sourceId?: string
  tags?: string
  fileSize?: number
  createTime: string
  updateTime: string
}

export type FavoriteType = 'note' | 'image' | 'link' | 'file' | 'message'

export function listFavorites() {
  return apiClient.get<never, ApiResult<FavoriteVO[]>>('/favorites')
}

export function addFavorite(payload: {
  title: string
  content: string
  type?: FavoriteType
  sourceType?: string
  sourceId?: string
  tags?: string
  fileSize?: number
}) {
  return apiClient.post<never, ApiResult<FavoriteVO>>('/favorites', payload)
}

export function removeFavorite(favoriteId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/favorites/${favoriteId}`)
}

export function updateFavorite(
  favoriteId: string,
  payload: {
    title?: string
    content?: string
    type?: FavoriteType
    tags?: string
    fileSize?: number
  }
) {
  return apiClient.put<never, ApiResult<FavoriteVO>>(`/favorites/${favoriteId}`, payload)
}
