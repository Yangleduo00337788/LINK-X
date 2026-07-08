import { defineStore } from 'pinia'
import type { ContactItem } from '../types'
import { contacts as initialContacts } from '../data/mockData'

export const useContactsStore = defineStore('contacts', {
  state: () => ({
    items: [...initialContacts] as ContactItem[]
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

    addByName(name: string) {
      const id = `contact-${Date.now()}`
      this.addContact({
        id,
        name,
        avatarText: name.charAt(0) || '?',
        avatarColor: '#12b7f5',
        group: '我的好友',
        online: true
      })
      return id
    },

    remove(id: string) {
      this.items = this.items.filter(c => c.id !== id)
    },

    syncFriendFromSession(session: { id: string; name: string; avatarText: string; avatarColor: string; online?: boolean }) {
      const exists = this.items.find(c => c.id === session.id || c.name === session.name)
      if (exists) return
      this.addContact({
        id: session.id,
        name: session.name,
        avatarText: session.avatarText,
        avatarColor: session.avatarColor,
        group: '我的好友',
        online: session.online
      })
    }
  },

  persist: {
    key: 'linkx-contacts',
    paths: ['items']
  }
})
