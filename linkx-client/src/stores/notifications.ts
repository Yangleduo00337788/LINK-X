/**
 * 通知 Store
 * 管理好友请求与群邀请两类系统通知及其处理状态
 */

import { defineStore } from 'pinia'
import type { FriendRequestItem } from '../types/friend'
import * as friendApi from '../api/friend'

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

const initialGroupNotifs: GroupNotification[] = [
  {
    id: 'gn1',
    groupName: '三角洲行动撞车沟通群',
    inviter: '清风',
    date: '2026/07/01',
    message: '邀请你加入群聊',
    status: '等待验证'
  }
]

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
    friendNotifs: [] as FriendNotification[],
    groupNotifs: [...initialGroupNotifs] as GroupNotification[],
    loading: false
  }),

  getters: {
    pendingFriendCount(state): number {
      return state.friendNotifs.filter(
        n => n.direction === 'incoming' && n.status === '等待验证'
      ).length
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
