import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface GroupInfo {
  id: string
  type: number
  name: string
  avatar?: string
  memberAvatars?: Array<{ nickname?: string; avatar?: string }>
  announcement?: string
  ownerId: string
  ownerNickname?: string
  memberCount: number
  lastMessage?: string
  lastMessageTime?: string | number
  /** 当前用户对本群备注 */
  myRemark?: string
  muteAll?: boolean
  muteAllStart?: number
  muteAllEnd?: number
  meMuted?: boolean
  meMuteUntil?: number
  /** 入群需审批 */
  joinApproval?: boolean
  /** 邀请策略 anyMember / ownerApprove */
  invitePolicy?: string
}

export interface GroupMember {
  userId: string
  nickname: string
  avatar?: string
  role: 'owner' | 'admin' | 'member'
  joinTime?: string | number
  muted?: boolean
  muteUntil?: number
}

export interface CreateGroupPayload {
  name: string
  memberIds: string[]
}

export interface ConversationSummary {
  id: string
  type: number
  name?: string
  avatar?: string
  lastMessage?: string
  lastMessageTime?: string | number
}

export interface UpdateGroupPayload {
  name?: string
  announcement?: string
}

export interface AddMembersPayload {
  memberIds: string[]
}

/**
 * 获取当前用户加入的群聊列表
 */
export function listGroups() {
  return apiClient.get<never, ApiResult<ConversationSummary[]>>('/group/list')
}

/**
 * 获取群详情
 */
export function getGroupInfo(conversationId: string) {
  return apiClient.get<never, ApiResult<GroupInfo>>(`/group/${conversationId}/info`)
}

/**
 * 创建群聊
 */
export function createGroup(payload: CreateGroupPayload) {
  return apiClient.post<never, ApiResult<GroupInfo>>('/group', payload)
}

/**
 * 更新群信息
 */
export function updateGroup(conversationId: string, payload: UpdateGroupPayload) {
  return apiClient.put<never, ApiResult<GroupInfo>>(`/group/${conversationId}`, payload)
}

/**
 * 获取群成员列表
 */
export function listGroupMembers(conversationId: string) {
  return apiClient.get<never, ApiResult<GroupMember[]>>(`/group/${conversationId}/members`)
}

/**
 * 添加群成员
 */
export function addGroupMembers(conversationId: string, payload: AddMembersPayload) {
  return apiClient.post<never, ApiResult<GroupMember[]>>(`/group/${conversationId}/members`, payload)
}

/**
 * 移除群成员
 */
export function removeGroupMember(conversationId: string, memberId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/group/${conversationId}/members/${memberId}`)
}

/**
 * 退出群聊
 */
export function quitGroup(conversationId: string) {
  return apiClient.post<never, ApiResult<null>>(`/group/${conversationId}/quit`)
}

/**
 * 解散群聊
 */
export function dissolveGroup(conversationId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/group/${conversationId}`)
}

/**
 * 转让群主
 */
export function transferGroupOwner(conversationId: string, newOwnerId: string) {
  return apiClient.post<never, ApiResult<null>>(`/group/${conversationId}/transfer`, null, {
    params: { newOwnerId }
  })
}

/**
 * 设置或取消管理员（仅群主；role: admin | member）
 */
export function updateMemberRole(
  conversationId: string,
  memberId: string,
  role: 'admin' | 'member'
) {
  return apiClient.put<never, ApiResult<null>>(`/group/${conversationId}/members/${memberId}/role`, {
    role
  })
}

/**
 * 全体禁言 / 定时全体禁言
 */
export function updateMuteAll(
  conversationId: string,
  payload: {
    enabled?: boolean
    startTime?: number
    endTime?: number
    clearSchedule?: boolean
  }
) {
  return apiClient.put<never, ApiResult<GroupInfo>>(`/group/${conversationId}/mute-all`, payload)
}

/**
 * 指定成员禁言
 */
export function updateMemberMute(
  conversationId: string,
  memberId: string,
  payload: { muted: boolean; muteUntil?: number }
) {
  return apiClient.put<never, ApiResult<null>>(
    `/group/${conversationId}/members/${memberId}/mute`,
    payload
  )
}

/**
 * 更新当前用户对本群备注
 */
export function updateGroupRemark(conversationId: string, remark: string) {
  return apiClient.put<never, ApiResult<string>>(`/group/${conversationId}/remark`, { remark })
}

export function batchRemoveMembers(conversationId: string, memberIds: string[]) {
  return apiClient.post<never, ApiResult<null>>(`/group/${conversationId}/members/batch-remove`, {
    memberIds
  })
}

export function batchMuteMembers(
  conversationId: string,
  memberIds: string[],
  muted: boolean
) {
  return apiClient.post<never, ApiResult<null>>(`/group/${conversationId}/members/batch-mute`, {
    memberIds,
    muted
  })
}

export function setJoinApproval(conversationId: string, required: boolean) {
  return apiClient.post<never, ApiResult<null>>(`/group/${conversationId}/join-approval`, {
    required
  })
}

export function markAnnouncementRead(conversationId: string) {
  return apiClient.post<never, ApiResult<null>>(`/group/${conversationId}/announcement/read`)
}

export function getAnnouncementReadCount(conversationId: string) {
  return apiClient.get<never, ApiResult<number>>(`/group/${conversationId}/announcement/read-count`)
}

export function setInvitePolicy(conversationId: string, policy: string) {
  return apiClient.post<never, ApiResult<null>>(`/group/${conversationId}/invite-policy`, {
    policy
  })
}

export interface GroupJoinRequestItem {
  applicantId: string
  applicantNickname?: string
  applicantAvatar?: string
  message?: string
  createTime?: string | number
  notificationId?: string
}

/** 申请加入群聊（开启入群审批时通知管理员） */
export function requestJoin(conversationId: string, message?: string) {
  return apiClient.post<never, ApiResult<null>>(`/group/${conversationId}/join-request`, {
    message
  })
}

/** 管理员查看待审批入群申请 */
export function listJoinRequests(conversationId: string) {
  return apiClient.get<never, ApiResult<GroupJoinRequestItem[]>>(
    `/group/${conversationId}/join-requests`
  )
}

/** 审批入群申请 */
export function handleJoinRequest(
  conversationId: string,
  applicantId: string,
  approve: boolean
) {
  return apiClient.post<never, ApiResult<null>>(
    `/group/${conversationId}/join-request/${applicantId}`,
    { approve }
  )
}
