/**
 * 通讯录 Store
 * 管理联系人列表、好友搜索，以及与聊天会话的同步
 */

import { defineStore } from 'pinia'
import type { ContactItem } from '../types'
import type { FriendItem } from '../types/friend'
import * as friendApi from '../api/friend'

const DEFAULT_AVATAR_COLOR = '#12b7f5'

function friendToContact(friend: FriendItem): ContactItem {
  const displayName = friend.remark?.trim() || friend.nickname || friend.username
  return {
    id: String(friend.userId),
    userId: friend.userId,
    name: displayName,
    avatarText: displayName.charAt(0) || '?',
    avatarColor: DEFAULT_AVATAR_COLOR,
    group: '我的好友',
    avatarUrl: friend.avatar
  }
}

export const useContactsStore = defineStore('contacts', {
  state: () => ({
    items: [] as ContactItem[],
    loading: false
  }),

  getters: {
    friends(state): ContactItem[] {
      return state.items.filter(c => c.group === '我的好友')
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

    reset() {
      this.items = []
      this.loading = false
    }
  },

  persist: {
    key: 'linkx-contacts',
    paths: ['items']
  }
})
