/**
 * 通知 Store
 * 管理好友请求、群邀请及消息通知三类通知
 */

import { defineStore } from 'pinia'
import type { FriendRequestItem } from '../types/friend'
import * as friendApi from '../api/friend'
import * as notificationApi from '../api/notification'
import * as groupInvitationApi from '../api/groupInvitation'
import { normalizeMediaUrl } from '../utils/mediaUrl'

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

/** 群邀请通知（来自 {@code /group/invitations}） */
export interface GroupNotification {
  id: string                // 邀请 id
  invitationId: string      // 同 id（兼容旧逻辑）
  conversationId: string
  groupName: string
  inviterUserId: string
  inviter: string           // 邀请人昵称（冗余）
  inviterAvatar?: string
  message?: string
  date: string              // 人类可读日期
  createTime: string
  status: '等待验证' | '已同意' | '已拒绝' | '已过期'
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

function mapInvitationStatus(status: number): GroupNotification['status'] {
  if (status === 1) return '已同意'
  if (status === 2) return '已拒绝'
  if (status === 3) return '已过期'
  return '等待验证'
}

function mapInvitation(item: groupInvitationApi.GroupInvitation): GroupNotification {
  const id = toIdString(item.id)
  return {
    id,
    invitationId: id,
    conversationId: toIdString(item.conversationId),
    groupName: item.groupName || '群聊',
    inviterUserId: toIdString(item.inviterUserId),
    inviter: item.inviterNickname || '用户',
    inviterAvatar: item.inviterAvatar,
    message: item.message || '',
    date: formatRequestDate(String(item.createTime ?? '')),
    createTime: String(item.createTime ?? ''),
    status: mapInvitationStatus(item.status)
  }
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

function mapRawNotification(n: notificationApi.MessageNotificationVO): MessageNotification {
  return {
    id: String(n.id),
    senderId: String(n.senderId),
    senderName: n.senderName || '用户',
    senderAvatar: normalizeMediaUrl(n.senderAvatar) || undefined,
    type: n.type,
    relatedId: n.relatedId ? String(n.relatedId) : undefined,
    content: n.content,
    readStatus: n.readStatus,
    createTime: String(n.createTime ?? '')
  }
}

export const useNotificationsStore = defineStore('notifications', {
  state: () => ({
    friendNotifs: [] as FriendNotification[],  // 好友请求（从后端加载）
    groupNotifs: [] as GroupNotification[],     // 群邀请（从后端加载）
    messageNotifs: [] as MessageNotification[], // 消息通知（朋友圈点赞/评论等）
    serverUnreadCount: 0 as number,             // 服务端未读计数（权威值）
    loading: false,
    /** 消息通知过滤："全部" | "@我"（仅 type=moments_mention） */
    mentionOnly: false as boolean
  }),

  getters: {
    pendingFriendCount(state): number {
      return state.friendNotifs.filter(
        n => n.direction === 'incoming' && n.status === '等待验证'
      ).length
    },
    unreadMessageCount(state): number {
      // 优先用服务端权威值；为 0 时也信任服务端（避免本地列表残留导致角标不消）
      if (typeof state.serverUnreadCount === 'number' && state.serverUnreadCount >= 0) {
        // 若本地已有列表，用本地未读数与服务端取较大值，防止推送后列表已更新但 count 稍慢
        const localUnread = state.messageNotifs.filter(n => n.readStatus === 0).length
        return Math.max(state.serverUnreadCount, localUnread)
      }
      return state.messageNotifs.filter(n => n.readStatus === 0).length
    },
    /** 友链相关未读（铃铛在友链窗口使用；含日程提醒） */
    momentsUnreadCount(state): number {
      const types = new Set(['moments_like', 'moments_comment', 'moments_mention', 'calendar_remind'])
      return state.messageNotifs.filter(n => n.readStatus === 0 && types.has(n.type)).length
    },
    /** 日历日程提醒列表（消息页虚拟会话用） */
    calendarRemindNotifs(state): MessageNotification[] {
      return state.messageNotifs.filter(n => n.type === 'calendar_remind')
    },
    /** 日历日程提醒未读数 */
    calendarRemindUnreadCount(state): number {
      return state.messageNotifs.filter(n => n.type === 'calendar_remind' && n.readStatus === 0).length
    },
    totalUnreadCount(): number {
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

    /**
     * 拉取当前用户收到的群邀请（真实后端 {@code GET /group/invitations}）。
     */
    async fetchGroupInvitations() {
      try {
        const res = await groupInvitationApi.listGroupInvitations()
        if (res.code === 200 && res.data) {
          this.groupNotifs = res.data.map(mapInvitation)
        }
      } catch (error) {
        console.error('获取群邀请失败:', error)
      }
    },

    /**
     * 接受群邀请：调用后端成功后刷新列表；
     * 前端调用方应自行跳转新群会话（通过 acceptGroupInvitation 返回值）。
     */
    async acceptGroupInvitationAction(invitationId: string) {
      const res = await groupInvitationApi.acceptGroupInvitation(invitationId)
      if (res.code !== 200) {
        throw new Error(res.message || '接受群邀请失败')
      }
      const n = this.groupNotifs.find(x => x.id === String(invitationId))
      if (n) n.status = '已同意'
      return res.data
    },

    async rejectGroupInvitationAction(invitationId: string) {
      const res = await groupInvitationApi.rejectGroupInvitation(invitationId)
      if (res.code !== 200) {
        throw new Error(res.message || '拒绝群邀请失败')
      }
      const n = this.groupNotifs.find(x => x.id === String(invitationId))
      if (n) n.status = '已拒绝'
    },

    async fetchMessageNotifications() {
      try {
        // 始终拉全量；「只看 @」在 UI 层过滤，避免消息页日历提醒被藏掉
        const [mineRes] = await Promise.all([
          notificationApi.listMineNotifications(false)
        ])
        if (mineRes.code === 200 && mineRes.data) {
          this.messageNotifs = mineRes.data.map(n => mapRawNotification(n))
        }
        void this.fetchNotificationCount()
      } catch (error) {
        console.error('获取消息通知失败:', error)
      }
    },

    /**
     * 设置"只收到@我的消息"开关（仅影响友链通知抽屉展示，不改本地全量缓存）
     */
    async setMentionOnly(value: boolean) {
      this.mentionOnly = value
    },

    /** 将全部日历日程提醒标为已读 */
    async markCalendarRemindsAsRead() {
      const unread = this.messageNotifs.filter(
        n => n.type === 'calendar_remind' && n.readStatus === 0
      )
      if (!unread.length) return
      await Promise.all(unread.map(n => this.markMessageAsRead(n.id)))
    },

    /**
     * 拉取服务端未读通知数（{@code GET /notifications/unread-count}），
     * 结果写入 {@link state.serverUnreadCount} 作为权威未读数；
     * 与本地列表计算的 {@code unreadMessageCount} 解耦，避免本地列表与服务端真实计数偏离。
     */
    async fetchNotificationCount() {
      try {
        const res = await notificationApi.getUnreadCount()
        if (res.code === 200 && res.data && typeof res.data.count === 'number') {
          this.serverUnreadCount = res.data.count
        }
      } catch (error) {
        console.error('获取未读通知数失败:', error)
      }
    },

    async markMessageAsRead(notificationId: string) {
      try {
        const res = await notificationApi.markAsRead(notificationId)
        if (res.code === 200) {
          const notif = this.messageNotifs.find(n => n.id === notificationId)
          if (notif && notif.readStatus === 0) {
            notif.readStatus = 1
            if (this.serverUnreadCount > 0) this.serverUnreadCount -= 1
          }
          void this.fetchNotificationCount()
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
          this.serverUnreadCount = 0
        }
      } catch (error) {
        console.error('标记全部通知已读失败:', error)
      }
    },

    async deleteMessageNotification(notificationId: string) {
      try {
        const res = await notificationApi.deleteNotification(notificationId)
        if (res.code === 200) {
          this.messageNotifs = this.messageNotifs.filter(n => n.id !== notificationId)
        }
      } catch (error) {
        console.error('删除通知失败:', error)
      }
    },

    /**
     * 调用后端清空当前用户全部消息通知,成功后清空本地 store。
     */
    async clearAllMessageNotifsRemote() {
      try {
        const res = await notificationApi.clearAllNotifications()
        if (res.code === 200) {
          this.messageNotifs = []
          this.serverUnreadCount = 0
          return res.data ?? this.messageNotifs.length
        }
      } catch (error) {
        console.error('清空通知失败:', error)
      }
      return 0
    },

    /**
     * WebSocket 收到 notification_refresh 时由 app store 透传调用,
     * 重新拉取最新一份并合并到本地。
     */
    async refreshFromSocket() {
      await Promise.all([
        this.fetchMessageNotifications(),
        this.fetchNotificationCount()
      ])
    },

    /**
     * 兼容老方法名。WebSocket 由 app store 统一管理,
     * 这里仅占位以避免已经调用方崩溃。
     */
    async initRealtime() {
      // noop: app store 已统一注册 onCustomAction
    },

    /** 兼容占位:WebSocket 由 app store 关闭 */
    teardownRealtime() {
      // noop
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
      return this.acceptGroupInvitationAction(id)
    },

    rejectGroup(id: string) {
      return this.rejectGroupInvitationAction(id)
    },

    clearFriendNotifs() {
      this.friendNotifs = []
    },

    clearGroupNotifs() {
      this.groupNotifs = []
    },

    clearMessageNotifs() {
      this.messageNotifs = []
      this.serverUnreadCount = 0
    },

    resetFriends() {
      this.friendNotifs = []
      this.groupNotifs = []
      this.loading = false
    }
  },

  // 不再持久化 groupNotifs：所有数据均来自后端，启动后由 fetchGroupInvitations 重新拉取
  persist: false
})
