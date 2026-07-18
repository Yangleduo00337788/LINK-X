import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface GroupInfo {
  id: string
  type: number
  name: string
  avatar?: string
  announcement?: string
  ownerId: string
  ownerNickname?: string
  memberCount: number
  lastMessage?: string
  lastMessageTime?: string | number
}

export interface GroupMember {
  userId: string
  nickname: string
  avatar?: string
  role: 'owner' | 'admin' | 'member'
  joinTime?: string | number
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
