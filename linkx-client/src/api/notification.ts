/**
 * 消息通知 API
 */
import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface MessageNotificationVO {
  id: number
  senderId: number
  senderName: string
  senderAvatar?: string
  type: string
  relatedId?: number
  content: string
  readStatus: number
  createTime: string
}

/**
 * 获取未读通知列表
 */
export function listUnreadNotifications() {
  return apiClient.get<never, ApiResult<MessageNotificationVO[]>>('/notifications/unread')
}

/**
 * 获取所有通知列表
 */
export function listAllNotifications() {
  return apiClient.get<never, ApiResult<MessageNotificationVO[]>>('/notifications')
}

/**
 * 获取消息通知（带过滤选项）
 *
 * @param mentionOnly true 时仅返回 @我的通知(type=moments_mention)，实现"只收到@我的消息"
 */
export function listMineNotifications(mentionOnly = false) {
  return apiClient.get<never, ApiResult<MessageNotificationVO[]>>('/notifications/mine', {
    params: { mentionOnly }
  })
}

/**
 * 获取未读通知数量
 */
export function getUnreadCount() {
  return apiClient.get<never, ApiResult<{ count: number }>>('/notifications/unread-count')
}

/**
 * 标记通知为已读
 */
export function markAsRead(notificationId: string | number) {
  return apiClient.post<never, ApiResult<null>>(`/notifications/${notificationId}/read`)
}

/**
 * 标记所有通知为已读
 */
export function markAllAsRead() {
  return apiClient.post<never, ApiResult<null>>('/notifications/read-all')
}

/**
 * 删除通知
 */
export function deleteNotification(notificationId: string | number) {
  return apiClient.delete<never, ApiResult<null>>(`/notifications/${notificationId}`)
}

/**
 * 清空当前用户全部通知。
 * @returns 清除条数
 */
export function clearAllNotifications() {
  return apiClient.delete<never, ApiResult<number>>('/notifications/clear')
}
