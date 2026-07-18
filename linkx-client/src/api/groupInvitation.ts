/**
 * 群邀请 API
 */
import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface GroupInvitation {
  id: string
  conversationId: string
  groupName?: string
  inviterUserId: string
  inviterNickname?: string
  inviterAvatar?: string
  message?: string
  status: 0 | 1 | 2 | 3
  createTime?: string | number
}

export interface InviteGroupPayload {
  inviteeUserId: string
  message?: string
}

/**
 * 列出当前用户收到的群邀请。
 */
export function listGroupInvitations() {
  return apiClient.get<never, ApiResult<GroupInvitation[]>>('/group/invitations')
}

/**
 * 接受邀请，返回新群会话信息。
 */
export function acceptGroupInvitation(invitationId: string) {
  return apiClient.post<never, ApiResult<unknown>>(`/group/invitations/${invitationId}/accept`)
}

/**
 * 拒绝邀请。
 */
export function rejectGroupInvitation(invitationId: string) {
  return apiClient.post<never, ApiResult<null>>(`/group/invitations/${invitationId}/reject`)
}

/**
 * 群成员邀请新成员入群。
 */
export function inviteToGroup(conversationId: string, payload: InviteGroupPayload) {
  return apiClient.post<never, ApiResult<GroupInvitation>>(
    `/group/invitations/${conversationId}`,
    payload
  )
}
