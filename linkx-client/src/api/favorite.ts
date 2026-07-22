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

export interface FavoriteStorageVO {
  usedBytes: number
  quotaBytes: number
  itemCount: number
  usedPercent: number
  typeCounts?: Record<string, number>
}

export interface FavoriteTagVO {
  id: string
  name: string
  color?: string
  sortOrder?: number
  preset?: boolean
  count?: number
}

export type FavoriteType = 'note' | 'image' | 'link' | 'file' | 'message'

export function listFavorites() {
  return apiClient.get<never, ApiResult<FavoriteVO[]>>('/favorites')
}

export function getFavoriteStorage() {
  return apiClient.get<never, ApiResult<FavoriteStorageVO>>('/favorites/storage')
}

export function listFavoriteTags() {
  return apiClient.get<never, ApiResult<FavoriteTagVO[]>>('/favorites/tags')
}

export function createFavoriteTag(payload: { name: string; color?: string }) {
  return apiClient.post<never, ApiResult<FavoriteTagVO>>('/favorites/tags', payload)
}

export function deleteFavoriteTag(tagId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/favorites/tags/${tagId}`)
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
