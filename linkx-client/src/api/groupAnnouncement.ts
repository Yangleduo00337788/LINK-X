import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface GroupAnnouncementVO {
  id: string
  conversationId: string
  content: string
  publisherId?: string
  publisherNickname?: string
  /** owner / admin / member */
  publisherRole?: string
  pinned?: boolean
  createTime?: string
  updateTime?: string
}

export function listGroupAnnouncements(conversationId: string) {
  return apiClient.get<never, ApiResult<GroupAnnouncementVO[]>>(
    `/group/${conversationId}/announcements`
  )
}

export function getDisplayAnnouncement(conversationId: string) {
  return apiClient.get<never, ApiResult<GroupAnnouncementVO | null>>(
    `/group/${conversationId}/announcements/display`
  )
}

export function createGroupAnnouncement(
  conversationId: string,
  payload: { content: string; pinned?: boolean }
) {
  return apiClient.post<never, ApiResult<GroupAnnouncementVO>>(
    `/group/${conversationId}/announcements`,
    payload
  )
}

export function updateGroupAnnouncement(
  conversationId: string,
  announcementId: string,
  payload: { content?: string; pinned?: boolean }
) {
  return apiClient.put<never, ApiResult<GroupAnnouncementVO>>(
    `/group/${conversationId}/announcements/${announcementId}`,
    payload
  )
}

export function deleteGroupAnnouncement(conversationId: string, announcementId: string) {
  return apiClient.delete<never, ApiResult<null>>(
    `/group/${conversationId}/announcements/${announcementId}`
  )
}
