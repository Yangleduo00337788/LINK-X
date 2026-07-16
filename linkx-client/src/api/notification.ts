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
 * 获取未读通知数量
 */
export function getUnreadCount() {
  return apiClient.get<never, ApiResult<{ count: number }>>('/notifications/unread-count')
}

/**
 * 标记通知为已读
 */
export function markAsRead(notificationId: number) {
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
export function deleteNotification(notificationId: number) {
  return apiClient.delete<never, ApiResult<null>>(`/notifications/${notificationId}`)
}
