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
  avatar?: string
  content: string
  time: string
  /** 被 @ 的用户 ID 列表 */
  mentions?: number[]
}

export interface PublishPayload {
  content: string
  images?: string[]
}

export interface CommentPayload {
  content: string
  parentId?: string
  /** 被 @ 的用户 ID 列表（字符串，避免雪花 ID 精度丢失） */
  mentions?: Array<string | number>
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

/**
 * 上传朋友圈图片
 * @param file 图片文件
 * @returns MinIO object key（发布时写入 images；列表接口会签发预签名 URL）
 */
export function uploadMomentsImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return apiClient.post<never, ApiResult<string>>('/moments/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 30000
  })
}
