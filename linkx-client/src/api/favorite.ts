/**
 * 收藏 API（复用笔记接口作为收藏存储）
 */

import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface FavoriteVO {
  id: string
  title: string
  content: string
  createTime: string
  updateTime: string
}

/**
 * 收藏类型映射到笔记类型
 * note=笔记, image=图片收藏, link=链接收藏, file=文件收藏
 */
export type FavoriteType = 'note' | 'image' | 'link' | 'file'

/**
 * 获取收藏列表
 */
export function listFavorites() {
  return apiClient.get<never, ApiResult<FavoriteVO[]>>('/notes')
}

/**
 * 添加收藏
 */
export function addFavorite(payload: { title: string; content: string }) {
  return apiClient.post<never, ApiResult<FavoriteVO>>('/notes', payload)
}

/**
 * 删除收藏
 */
export function removeFavorite(favoriteId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/notes/${favoriteId}`)
}

/**
 * 更新收藏
 */
export function updateFavorite(favoriteId: string, payload: { title?: string; content?: string }) {
  return apiClient.put<never, ApiResult<FavoriteVO>>(`/notes/${favoriteId}`, payload)
}
