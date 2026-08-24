/**
 * 作者：yangleduo
 */
import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface ShortVideoPost {
  id: string
  userId: string
  nickname?: string
  avatar?: string
  description: string
  videoUrl?: string
  coverUrl?: string
  durationMs?: number
  visibility?: number
  playCount?: number
  time: string
  likes: number
  liked: boolean
  followingAuthor?: boolean
  commentCount?: number
  comments: ShortVideoComment[]
}

export interface ShortVideoComment {
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

export interface PublishShortVideoPayload {
  description?: string
  videoKey: string
  coverKey?: string
  durationMs?: number
  visibility?: number
}

export interface UpdateShortVideoPayload {
  description?: string
  coverKey?: string
  visibility?: number
}

export interface CommentShortVideoPayload {
  content: string
  parentId?: string
  mentions?: Array<string | number>
}

export interface ListShortVideoParams {
  beforeId?: string
  limit?: number
  q?: string
}

const SHORT_VIDEO_READ_TIMEOUT = 30000

export function listShortVideos(params?: ListShortVideoParams) {
  return apiClient.get<never, ApiResult<ShortVideoPost[]>>('/short-video', {
    params,
    timeout: SHORT_VIDEO_READ_TIMEOUT
  })
}

export function listFollowingShortVideos(params?: Omit<ListShortVideoParams, 'q'>) {
  return apiClient.get<never, ApiResult<ShortVideoPost[]>>('/short-video/following', {
    params,
    timeout: SHORT_VIDEO_READ_TIMEOUT
  })
}

export function listFriendsShortVideos(params?: Omit<ListShortVideoParams, 'q'>) {
  return apiClient.get<never, ApiResult<ShortVideoPost[]>>('/short-video/friends', {
    params,
    timeout: SHORT_VIDEO_READ_TIMEOUT
  })
}

export function listUserShortVideos(userId: string, params?: Omit<ListShortVideoParams, 'q'>) {
  return apiClient.get<never, ApiResult<ShortVideoPost[]>>(`/short-video/user/${userId}`, {
    params,
    timeout: SHORT_VIDEO_READ_TIMEOUT
  })
}

export function getShortVideo(postId: string) {
  return apiClient.get<never, ApiResult<ShortVideoPost>>(`/short-video/${postId}`, {
    timeout: SHORT_VIDEO_READ_TIMEOUT
  })
}

export function publishShortVideo(payload: PublishShortVideoPayload) {
  return apiClient.post<never, ApiResult<ShortVideoPost>>('/short-video', payload)
}

export function deleteShortVideo(postId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/short-video/${postId}`)
}

export function updateShortVideo(postId: string, payload: UpdateShortVideoPayload) {
  return apiClient.put<never, ApiResult<ShortVideoPost>>(`/short-video/${postId}`, payload)
}

export function likeShortVideo(postId: string) {
  return apiClient.post<never, ApiResult<null>>(`/short-video/${postId}/like`)
}

export function unlikeShortVideo(postId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/short-video/${postId}/like`)
}

export function commentShortVideo(postId: string, payload: CommentShortVideoPayload) {
  return apiClient.post<never, ApiResult<ShortVideoComment>>(`/short-video/${postId}/comment`, payload)
}

export function listShortVideoComments(postId: string, params?: { beforeId?: string; limit?: number }) {
  return apiClient.get<never, ApiResult<ShortVideoComment[]>>(`/short-video/${postId}/comments`, { params })
}

export function deleteShortVideoComment(commentId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/short-video/comment/${commentId}`)
}

export function followShortVideoAuthor(userId: string) {
  return apiClient.post<never, ApiResult<null>>(`/short-video/follow/${userId}`)
}

export function unfollowShortVideoAuthor(userId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/short-video/follow/${userId}`)
}

export function recordShortVideoPlay(postId: string) {
  return apiClient.post<never, ApiResult<null>>(`/short-video/${postId}/play`)
}

export function uploadShortVideoMedia(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return apiClient.post<never, ApiResult<string>>('/short-video/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000
  })
}

export function shortVideoContentUrl(postId: string, kind: 'video' | 'cover') {
  const base = apiClient.defaults.baseURL || ''
  const path = kind === 'video'
    ? `/short-video/${postId}/video/content`
    : `/short-video/${postId}/cover/content`
  return `${base}${path}`
}
