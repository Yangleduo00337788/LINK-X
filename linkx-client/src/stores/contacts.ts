/**
 * 通讯录 Store
 * 管理联系人列表、好友搜索，以及与聊天会话的同步
 */

import { defineStore } from 'pinia'
import type { ContactItem } from '../types'
import type { FriendItem } from '../types/friend'
import * as friendApi from '../api/friend'
import { normalizeMediaUrl } from '../utils/mediaUrl'
import { sanitizeContactsPersistState } from '../utils/persistSanitize'
import { formatFriendDisplayName, friendAvatarText } from '../utils/friendDisplay'

const DEFAULT_AVATAR_COLOR = '#12b7f5'
const DEFAULT_FRIEND_GROUP = '我的好友'

function friendToContact(friend: FriendItem): ContactItem {
  const nickname = friend.nickname || friend.username
  const remark = friend.remark?.trim() || ''
  const displayName = formatFriendDisplayName(nickname, remark)
  const group = friend.groupName?.trim() || DEFAULT_FRIEND_GROUP
  return {
    id: String(friend.userId),
    userId: friend.userId,
    name: displayName,
    nickname,
    remark: remark || undefined,
    avatarText: friendAvatarText(nickname, remark),
    avatarColor: DEFAULT_AVATAR_COLOR,
    group,
    avatarUrl: normalizeMediaUrl(friend.avatar) || undefined,
    online: !!friend.online
  }
}

export const useContactsStore = defineStore('contacts', {
  state: () => ({
    items: [] as ContactItem[],
    loading: false
  }),

  getters: {
    friends(state): ContactItem[] {
      return state.items
    },

    /** 当前在线好友（受对方「在线状态可见」约束） */
    onlineFriends(state): ContactItem[] {
      return state.items.filter(c => c.online)
    },

    /** 已有分组名（去重，默认组优先） */
    friendGroupNames(state): string[] {
      const set = new Set<string>()
      for (const c of state.items) {
        set.add((c.group || DEFAULT_FRIEND_GROUP).trim() || DEFAULT_FRIEND_GROUP)
      }
      const names = [...set]
      names.sort((a, b) => {
        if (a === DEFAULT_FRIEND_GROUP) return -1
        if (b === DEFAULT_FRIEND_GROUP) return 1
        return a.localeCompare(b, 'zh-CN')
      })
      return names
    },

    searchUsers: state => (keyword: string) => {
      const q = keyword.trim().toLowerCase()
      if (!q) return state.items
      return state.items.filter(c => c.name.toLowerCase().includes(q))
    }
  },

  actions: {
    addContact(contact: ContactItem) {
      if (this.items.some(c => c.id === contact.id)) return
      this.items.push(contact)
    },

    remove(id: string) {
      this.items = this.items.filter(c => c.id !== id)
    },

    removeByUserId(userId: string) {
      this.items = this.items.filter(c => String(c.userId ?? c.id) !== userId)
    },

    async deleteFriend(userId: string) {
      const res = await friendApi.deleteFriend(userId)
      if (res.code !== 200) {
        throw new Error(res.message || '删除好友失败')
      }
      this.removeByUserId(userId)
    },

    async updateFriendRemark(userId: string, remark: string) {
      const res = await friendApi.updateFriendRemark(userId, remark)
      if (res.code !== 200) {
        throw new Error(res.message || '保存备注失败')
      }
      const value = (res.data ?? remark).trim()
      const idx = this.items.findIndex(c => String(c.userId ?? c.id) === String(userId))
      if (idx >= 0) {
        const prev = this.items[idx]
        const nickname = prev.nickname || (!prev.remark ? prev.name : '') || '好友'
        const displayName = formatFriendDisplayName(nickname, value)
        this.items.splice(idx, 1, {
          ...prev,
          remark: value || undefined,
          nickname,
          name: displayName,
          avatarText: friendAvatarText(nickname, value)
        })
      }
      return value
    },

    async updateFriendGroup(userId: string, groupName: string) {
      const res = await friendApi.updateFriendGroup(userId, groupName)
      if (res.code !== 200) {
        throw new Error(res.message || '保存分组失败')
      }
      const value = (res.data ?? groupName).trim() || DEFAULT_FRIEND_GROUP
      const idx = this.items.findIndex(c => String(c.userId ?? c.id) === String(userId))
      if (idx >= 0) {
        const prev = this.items[idx]
        this.items.splice(idx, 1, { ...prev, group: value })
      }
      return value
    },

    syncFriendFromSession(session: {
      id: string
      name: string
      avatarText: string
      avatarColor: string
      online?: boolean
      avatarUrl?: string
    }) {
      const exists = this.items.find(c => c.id === session.id || c.name === session.name)
      if (exists) return

      const userId = /^\d+$/.test(session.id) ? session.id : undefined
      this.addContact({
        id: session.id,
        userId,
        name: session.name,
        avatarText: session.avatarText,
        avatarColor: session.avatarColor,
        group: '我的好友',
        online: session.online,
        avatarUrl: session.avatarUrl
      })
    },

    async fetchFriends() {
      this.loading = true
      try {
        const res = await friendApi.listFriends()
        if (res.code === 200 && res.data) {
          this.items = res.data.map(friendToContact)
        }
      } catch (error) {
        console.error('获取好友列表失败:', error)
      } finally {
        this.loading = false
      }
    },

    /**
     * 实时更新好友在线状态（WS presence 推送）
     * @returns 状态是否发生变化（用于上线提醒等）
     */
    setOnline(userId: string | number, online: boolean): boolean {
      const id = String(userId)
      const idx = this.items.findIndex(c => String(c.userId ?? c.id) === id)
      if (idx < 0) return false
      const prev = this.items[idx]
      if (prev.online === online) return false
      // 替换元素，确保虚拟列表等依赖 items 引用变化的视图能刷新
      this.items.splice(idx, 1, { ...prev, online })
      return true
    },

    reset() {
      this.items = []
      this.loading = false
    }
  },

  persist: {
    key: 'linkx-contacts',
    paths: ['items'],
    serializer: {
      serialize: value =>
        JSON.stringify(
          sanitizeContactsPersistState(
            value as { items?: Array<{ avatarUrl?: string; [k: string]: unknown }> }
          )
        ),
      deserialize: value =>
        sanitizeContactsPersistState(
          JSON.parse(value) as { items?: Array<{ avatarUrl?: string; [k: string]: unknown }> }
        )
    }
  }
})
