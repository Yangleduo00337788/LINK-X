import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface MomentsPost {
  id: string
  userId: string
  nickname?: string
  avatar?: string
  content: string
  images?: string[]
  time: string
  likes: number
  liked: boolean
  likedBy: string[]
  comments: MomentsComment[]
}

export interface MomentsComment {
  id: string
  userId: string
  nickname?: string
  content: string
  time: string
}

export interface PublishPayload {
  content: string
  images?: string[]
}

export interface CommentPayload {
  content: string
  parentId?: string
}

/**
 * 获取朋友圈动态列表
 */
export function listMoments() {
  return apiClient.get<never, ApiResult<MomentsPost[]>>('/moments')
}

/**
 * 获取指定用户的动态
 */
export function getUserMoments(userId: string) {
  return apiClient.get<never, ApiResult<MomentsPost[]>>(`/moments/user/${userId}`)
}

/**
 * 发布动态
 */
export function publishMoments(payload: PublishPayload) {
  return apiClient.post<never, ApiResult<MomentsPost>>('/moments', payload)
}

/**
 * 删除动态
 */
export function deleteMoments(postId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/moments/${postId}`)
}

/**
 * 点赞
 */
export function likeMoments(postId: string) {
  return apiClient.post<never, ApiResult<null>>(`/moments/${postId}/like`)
}

/**
 * 取消点赞
 */
export function unlikeMoments(postId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/moments/${postId}/like`)
}

/**
 * 评论动态
 */
export function commentMoments(postId: string, payload: CommentPayload) {
  return apiClient.post<never, ApiResult<MomentsComment>>(`/moments/${postId}/comment`, payload)
}

/**
 * 删除评论
 */
export function deleteComment(commentId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/moments/comment/${commentId}`)
}
