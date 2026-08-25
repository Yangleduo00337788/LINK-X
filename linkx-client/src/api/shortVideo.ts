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
  shares?: number
  time: string
  likes: number
  liked: boolean
  favorites?: number
  favorited?: boolean
  followingAuthor?: boolean
  commentCount?: number
  comments: ShortVideoComment[]
  topics?: string[]
}

export interface ShortVideoTopic {
  name: string
  displayName?: string
  postCount?: number
  pinned?: boolean
}

export interface ShortVideoTopicPage {
  items: ShortVideoTopic[]
  page: number
  size: number
  total: number
}

export interface ShortVideoAuthorProfile {
  userId: string
  nickname?: string
  avatar?: string
  postCount?: number
  followerCount?: number
  followingAuthor?: boolean
}

export function shortVideoTopicLabel(topic: Pick<ShortVideoTopic, 'name' | 'displayName'>) {
  const label = topic.displayName?.trim()
  return label || topic.name
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
  imageUrl?: string
  likes?: number
  liked?: boolean
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
  content?: string
  parentId?: string
  mentions?: Array<string | number>
  imageKey?: string
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

export function listHotShortVideoTopics(limit = 10) {
  return apiClient.get<never, ApiResult<ShortVideoTopic[]>>('/short-video/topics/hot', {
    params: { limit },
    timeout: SHORT_VIDEO_READ_TIMEOUT
  })
}

export function listHotShortVideos(limit = 10) {
  return apiClient.get<never, ApiResult<ShortVideoPost[]>>('/short-video/hot', {
    params: { limit },
    timeout: SHORT_VIDEO_READ_TIMEOUT
  })
}

export function listShortVideoTopics(params?: { page?: number; limit?: number }) {
  return apiClient.get<never, ApiResult<ShortVideoTopicPage>>('/short-video/topics', {
    params,
    timeout: SHORT_VIDEO_READ_TIMEOUT
  })
}

export function getShortVideoTopic(name: string) {
  const encoded = encodeURIComponent(name.replace(/^[#＃]/, '').trim())
  return apiClient.get<never, ApiResult<ShortVideoTopic>>(`/short-video/topics/${encoded}`, {
    timeout: SHORT_VIDEO_READ_TIMEOUT
  })
}

export function listFollowingShortVideos(params?: Omit<ListShortVideoParams, 'q'>) {
  return apiClient.get<never, ApiResult<ShortVideoPost[]>>('/short-video/following', {
    params,
    timeout: SHORT_VIDEO_READ_TIMEOUT
  })
}

export interface ShortVideoFollowingUser {
  followId: string
  userId: string
  nickname?: string
  avatar?: string
  postCount?: number
}

export function listFollowingShortVideoUsers(params?: { beforeId?: string; limit?: number }) {
  return apiClient.get<never, ApiResult<ShortVideoFollowingUser[]>>('/short-video/following/users', {
    params,
    timeout: SHORT_VIDEO_READ_TIMEOUT
  })
}

export function countFollowingShortVideoUsers() {
  return apiClient.get<never, ApiResult<number>>('/short-video/following/count', {
    timeout: SHORT_VIDEO_READ_TIMEOUT
  })
}

export function listFriendsShortVideos(params?: Omit<ListShortVideoParams, 'q'>) {
  return apiClient.get<never, ApiResult<ShortVideoPost[]>>('/short-video/friends', {
    params,
    timeout: SHORT_VIDEO_READ_TIMEOUT
  })
}

export function listFavoriteShortVideos(params?: Omit<ListShortVideoParams, 'q'>) {
  return apiClient.get<never, ApiResult<ShortVideoPost[]>>('/short-video/favorites', {
    params,
    timeout: SHORT_VIDEO_READ_TIMEOUT
  })
}

export function listLikedShortVideos(params?: Omit<ListShortVideoParams, 'q'>) {
  return apiClient.get<never, ApiResult<ShortVideoPost[]>>('/short-video/likes', {
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

export function getShortVideoAuthorProfile(userId: string) {
  return apiClient.get<never, ApiResult<ShortVideoAuthorProfile>>(`/short-video/user/${userId}/profile`, {
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

export function favoriteShortVideo(postId: string) {
  return apiClient.post<never, ApiResult<null>>(`/short-video/${postId}/favorite`)
}

export function unfavoriteShortVideo(postId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/short-video/${postId}/favorite`)
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

export function likeShortVideoComment(commentId: string) {
  return apiClient.post<never, ApiResult<null>>(`/short-video/comment/${commentId}/like`)
}

export function unlikeShortVideoComment(commentId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/short-video/comment/${commentId}/like`)
}

export function followShortVideoAuthor(userId: string) {
  return apiClient.post<never, ApiResult<null>>(`/short-video/follow/${userId}`)
}

export function unfollowShortVideoAuthor(userId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/short-video/follow/${userId}`)
}

export function markShortVideoNotInterested(postId: string) {
  return apiClient.post<never, ApiResult<null>>(`/short-video/${postId}/not-interested`)
}

export function blockShortVideoAuthor(userId: string) {
  return apiClient.post<never, ApiResult<null>>(`/short-video/block/${userId}`)
}

export interface ReportShortVideoPayload {
  reason: string
  detail?: string
  imageKeys?: string[]
}

export function reportShortVideo(postId: string, payload: ReportShortVideoPayload) {
  return apiClient.post<ReportShortVideoPayload, ApiResult<null>>(`/short-video/${postId}/report`, payload)
}

export function recordShortVideoPlay(postId: string) {
  return apiClient.post<never, ApiResult<null>>(`/short-video/${postId}/play`)
}

export function recordShortVideoShare(postId: string) {
  return apiClient.post<never, ApiResult<null>>(`/short-video/${postId}/share`)
}

export function shareShortVideoToChat(
  postId: string,
  payload: { conversationIds: string[]; leaveMessage?: string }
) {
  return apiClient.post<never, ApiResult<unknown>>(`/short-video/${postId}/share-chat`, payload)
}

export function uploadShortVideoMedia(file: File, onProgress?: (percent: number) => void) {
  const formData = new FormData()
  formData.append('file', file)
  return apiClient.post<never, ApiResult<string>>('/short-video/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000,
    onUploadProgress: event => {
      if (!onProgress || !event.total) return
      onProgress(Math.min(100, Math.round((event.loaded / event.total) * 100)))
    }
  })
}

export function shortVideoContentUrl(postId: string, kind: 'video' | 'cover') {
  const base = apiClient.defaults.baseURL || ''
  const path = kind === 'video'
    ? `/short-video/${postId}/video/content`
    : `/short-video/${postId}/cover/content`
  return `${base}${path}`
}
