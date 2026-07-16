/**
 * 通知 Store
 * 管理好友请求、群邀请及消息通知三类通知
 */

import { defineStore } from 'pinia'
import type { FriendRequestItem } from '../types/friend'
import * as friendApi from '../api/friend'
import * as notificationApi from '../api/notification'

/** 好友相关通知（加好友、验证等） */
export interface FriendNotification {
  id: string
  requestId: string
  fromUserId: string
  peerUserId: string
  direction: 'incoming' | 'outgoing'
  avatar: string
  name: string
  action: string
  date: string
  createTime: string
  message: string
  source: string
  status: '等待验证' | '已同意' | '已拒绝'
}

/** 群邀请通知 */
export interface GroupNotification {
  id: string
  groupName: string
  inviter: string
  date: string
  message: string
  status: '等待验证' | '已同意' | '已拒绝'
}

/** 消息通知 */
export interface MessageNotification {
  id: string
  senderId: string
  senderName: string
  senderAvatar?: string
  type: string
  relatedId?: string
  content: string
  readStatus: number
  createTime: string
}

/** 通知类型 */
export type NotificationType = 'like' | 'comment' | 'follow' | 'mention' | 'system'

function formatRequestDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const y = date.getFullYear()
  const m = `${date.getMonth() + 1}`.padStart(2, '0')
  const d = `${date.getDate()}`.padStart(2, '0')
  return `${y}/${m}/${d}`
}

function mapRequestStatus(status: FriendRequestItem['status']): FriendNotification['status'] {
  if (status === 1) return '已同意'
  if (status === 2) return '已拒绝'
  return '等待验证'
}

function toIdString(value: string | number | undefined | null): string {
  if (value == null) return ''
  return typeof value === 'string' ? value : String(value)
}

function mapRequestItem(item: FriendRequestItem): FriendNotification {
  const isIncoming = item.direction === 'incoming'
  const peerUserId = item.peerUserId ?? (isIncoming ? item.fromUserId : item.toUserId)
  const requestId = toIdString(item.id)
  return {
    id: requestId,
    requestId,
    fromUserId: toIdString(item.fromUserId),
    peerUserId: toIdString(peerUserId),
    direction: item.direction,
    avatar: item.peerAvatar || '/default-avatar.svg',
    name: item.peerNickname || item.peerUsername,
    action: isIncoming ? '请求加为好友' : '正在验证你的邀请',
    date: formatRequestDate(item.createTime),
    createTime: item.createTime,
    message: item.message || (isIncoming ? '' : '请求添加对方为好友'),
    source: '',
    status: mapRequestStatus(item.status)
  }
}

export const useNotificationsStore = defineStore('notifications', {
  state: () => ({
    friendNotifs: [] as FriendNotification[],  // 好友请求（从后端加载）
    groupNotifs: [] as GroupNotification[],     // 群邀请（从后端加载）
    messageNotifs: [] as MessageNotification[], // 消息通知（朋友圈点赞/评论等）
    loading: false
  }),

  getters: {
    pendingFriendCount(state): number {
      return state.friendNotifs.filter(
        n => n.direction === 'incoming' && n.status === '等待验证'
      ).length
    },
    unreadMessageCount(state): number {
      return state.messageNotifs.filter(n => n.readStatus === 0).length
    },
    totalUnreadCount(state): number {
      return this.pendingFriendCount + this.unreadMessageCount
    }
  },

  actions: {
    async fetchFriendRequests() {
      this.loading = true
      try {
        const [incomingRes, outgoingRes] = await Promise.all([
          friendApi.listIncomingRequests(),
          friendApi.listOutgoingRequests()
        ])
        const incoming = incomingRes.code === 200 && incomingRes.data ? incomingRes.data : []
        const outgoing = outgoingRes.code === 200 && outgoingRes.data ? outgoingRes.data : []
        this.friendNotifs = [...incoming, ...outgoing]
          .map(mapRequestItem)
          .sort((a, b) => new Date(b.createTime).getTime() - new Date(a.createTime).getTime())
      } catch (error) {
        console.error('获取好友通知失败:', error)
      } finally {
        this.loading = false
      }
    },

    async fetchMessageNotifications() {
      try {
        const res = await notificationApi.listUnreadNotifications()
        if (res.code === 200 && res.data) {
          this.messageNotifs = res.data.map(n => ({
            id: String(n.id),
            senderId: String(n.senderId),
            senderName: n.senderName,
            senderAvatar: n.senderAvatar,
            type: n.type,
            relatedId: n.relatedId ? String(n.relatedId) : undefined,
            content: n.content,
            readStatus: n.readStatus,
            createTime: n.createTime
          }))
        }
      } catch (error) {
        console.error('获取消息通知失败:', error)
      }
    },

    async markMessageAsRead(notificationId: string) {
      try {
        const res = await notificationApi.markAsRead(Number(notificationId))
        if (res.code === 200) {
          const notif = this.messageNotifs.find(n => n.id === notificationId)
          if (notif) {
            notif.readStatus = 1
          }
        }
      } catch (error) {
        console.error('标记通知已读失败:', error)
      }
    },

    async markAllMessagesAsRead() {
      try {
        const res = await notificationApi.markAllAsRead()
        if (res.code === 200) {
          this.messageNotifs.forEach(n => {
            n.readStatus = 1
          })
        }
      } catch (error) {
        console.error('标记全部通知已读失败:', error)
      }
    },

    async deleteMessageNotification(notificationId: string) {
      try {
        const res = await notificationApi.deleteNotification(Number(notificationId))
        if (res.code === 200) {
          this.messageNotifs = this.messageNotifs.filter(n => n.id !== notificationId)
        }
      } catch (error) {
        console.error('删除通知失败:', error)
      }
    },

    findFriendNotif(id: string) {
      return this.friendNotifs.find(x => x.id === id)
    },

    async acceptFriendRequest(requestId: string) {
      const res = await friendApi.acceptFriendRequest(requestId)
      if (res.code !== 200) {
        throw new Error(res.message || '同意好友申请失败')
      }
      await this.fetchFriendRequests()
      return this.friendNotifs.find(n => n.requestId === requestId)
    },

    async rejectFriendRequest(requestId: string) {
      const res = await friendApi.rejectFriendRequest(requestId)
      if (res.code !== 200) {
        throw new Error(res.message || '拒绝好友申请失败')
      }
      await this.fetchFriendRequests()
      return this.friendNotifs.find(n => n.requestId === requestId)
    },

    acceptGroup(id: string) {
      const n = this.groupNotifs.find(x => x.id === id)
      if (n) n.status = '已同意'
      return n
    },

    rejectGroup(id: string) {
      const n = this.groupNotifs.find(x => x.id === id)
      if (n) n.status = '已拒绝'
    },

    clearFriendNotifs() {
      this.friendNotifs = []
    },

    clearGroupNotifs() {
      this.groupNotifs = []
    },

    clearMessageNotifs() {
      this.messageNotifs = []
    },

    resetFriends() {
      this.friendNotifs = []
      this.loading = false
    }
  },

  persist: {
    key: 'linkx-notifications',
    paths: ['groupNotifs']
  }
})
