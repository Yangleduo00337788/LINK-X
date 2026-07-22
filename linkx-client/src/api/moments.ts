import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface MomentsPost {
  id: string
  userId: string
  nickname?: string
  avatar?: string
  content: string
  images?: string[]
  location?: string
  atUsers?: string
  atUserNames?: string[]
  visibility?: number
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
  mentions?: number[]
  parentId?: string
  replyToNickname?: string
}

export interface PublishPayload {
  content: string
  images?: string[]
  location?: string
  atUsers?: number[]
  visibility?: number
}

export interface UpdatePayload {
  content?: string
  images?: string[]
  location?: string
  atUsers?: number[]
  visibility?: number
}

export interface CommentPayload {
  content: string
  parentId?: string
  mentions?: Array<string | number>
}

export interface ListMomentsParams {
  beforeId?: string
  limit?: number
  q?: string
}

export function listMoments(params?: ListMomentsParams) {
  return apiClient.get<never, ApiResult<MomentsPost[]>>('/moments', { params })
}

export function getUserMoments(userId: string, params?: ListMomentsParams) {
  return apiClient.get<never, ApiResult<MomentsPost[]>>(`/moments/user/${userId}`, { params })
}

export function publishMoments(payload: PublishPayload) {
  return apiClient.post<never, ApiResult<MomentsPost>>('/moments', payload)
}

export function updateMoments(postId: string, payload: UpdatePayload) {
  return apiClient.put<never, ApiResult<MomentsPost>>(`/moments/${postId}`, payload)
}

export function deleteMoments(postId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/moments/${postId}`)
}

export function likeMoments(postId: string) {
  return apiClient.post<never, ApiResult<null>>(`/moments/${postId}/like`)
}

export function unlikeMoments(postId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/moments/${postId}/like`)
}

export function commentMoments(postId: string, payload: CommentPayload) {
  return apiClient.post<never, ApiResult<MomentsComment>>(`/moments/${postId}/comment`, payload)
}

export function deleteComment(commentId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/moments/comment/${commentId}`)
}

/** 上传朋友圈图片或视频，返回 object key */
export function uploadMomentsMedia(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return apiClient.post<never, ApiResult<string>>('/moments/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

/** @deprecated 使用 uploadMomentsMedia */
export function uploadMomentsImage(file: File) {
  return uploadMomentsMedia(file)
}
