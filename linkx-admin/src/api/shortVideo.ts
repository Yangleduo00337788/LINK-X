/**
 * 作者：yangleduo
 */
import { del, get } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export interface ShortVideoPostItem {
  id: string
  userId?: string
  username?: string
  nickname?: string
  description?: string
  visibility?: number
  playCount?: number
  shareCount?: number
  likeCount?: number
  favoriteCount?: number
  commentCount?: number
  durationMs?: number
  transcodeStatus?: string
  videoUrl?: string
  coverUrl?: string
  createTime?: string
}

export interface ShortVideoCommentItem {
  id: string
  postId?: string
  userId?: string
  username?: string
  nickname?: string
  content?: string
  parentId?: string
  likeCount?: number
  postCoverUrl?: string
  createTime?: string
}

export function listShortVideoPosts(params: PageQuery & {
  userId?: string
  visibility?: number
  transcodeStatus?: string
}) {
  return get<PageResult<ShortVideoPostItem>>('/admin/short-video/posts', params as Record<string, unknown>)
}

export function getShortVideoPost(id: string) {
  return get<ShortVideoPostItem>(`/admin/short-video/posts/${id}`)
}

export function deleteShortVideoPost(id: string) {
  return del<null>(`/admin/short-video/posts/${id}`)
}

export function listShortVideoComments(params: PageQuery & {
  postId?: string
  userId?: string
}) {
  return get<PageResult<ShortVideoCommentItem>>('/admin/short-video/comments', params as Record<string, unknown>)
}

export function deleteShortVideoComment(id: string) {
  return del<null>(`/admin/short-video/comments/${id}`)
}
