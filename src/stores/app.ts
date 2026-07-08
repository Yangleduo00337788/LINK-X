import { defineStore } from 'pinia'
import type { NavKey, ChatSession, ChatMessage } from '../types'
import { initialSessions, initialMessages } from '../data/mockData'

export const useAppStore = defineStore('app', {
  state: () => ({
    navKey: 'chat' as NavKey,
    sessions: [...initialSessions] as ChatSession[],
    messagesBySession: { ...initialMessages } as Record<string, ChatMessage[]>,
    currentSessionId: null as string | null,
    theme: 'light' as 'light' | 'dark',
    contactsActiveView: 'none' as 'none' | 'friend-notifs' | 'group-notifs',
    userProfile: {
      nickname: '晚香玉',
      signature: '编辑个性签名'
    },
    isLoggedIn: true,
    isLoading: false,
    isOffline: false,
    isLocked: false
  }),

  getters: {
    currentSession(state): ChatSession | null {
      return state.sessions.find(s => s.id === state.currentSessionId) ?? null
    },
    currentMessages(state): ChatMessage[] {
      const id = state.currentSessionId
      if (!id) return []
      return state.messagesBySession[id] ?? []
    }
  },

  actions: {
    setNav(key: NavKey) {
      this.navKey = key
    },

    selectSession(session: ChatSession) {
      this.currentSessionId = session.id
      const s = this.sessions.find(x => x.id === session.id)
      if (s?.unread) {
        s.unread = 0
      }
      if (!this.messagesBySession[session.id]) {
        this.messagesBySession[session.id] = []
      }
    },

    ensureSession(session: ChatSession) {
      const exists = this.sessions.find(s => s.id === session.id)
      if (!exists) {
        this.sessions.unshift(session)
      }
      this.selectSession(session)
      this.navKey = 'chat'
    },

    sendMessage(text: string, type: ChatMessage['type'] = 'text', replyTo?: ChatMessage) {
      const id = this.currentSessionId
      if (!id || !text.trim()) return
      const now = new Date()
      const time = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`
      const msg: ChatMessage = {
        id: `msg-${Date.now()}`,
        sessionId: id,
        content: text.trim(),
        time,
        isSelf: true,
        type,
        replyTo
      }
      if (!this.messagesBySession[id]) {
        this.messagesBySession[id] = []
      }
      this.messagesBySession[id].push(msg)
      const session = this.sessions.find(s => s.id === id)
      if (session) {
        session.lastMessage = text.trim()
        session.time = time
      }
    },

    toggleTheme() {
      this.theme = this.theme === 'light' ? 'dark' : 'light'
      document.documentElement.setAttribute('data-theme', this.theme)
    },

    updateSignature(text: string) {
      this.userProfile.signature = text
    },

    updateNickname(name: string) {
      this.userProfile.nickname = name
    },

    logout() {
      this.isLoggedIn = false
    },

    login() {
      this.isLoggedIn = true
      this.isLoading = true
      setTimeout(() => {
        this.isLoading = false
      }, 1500)
    },

    lock() {
      this.isLocked = true
    },

    unlock() {
      this.isLocked = false
    },

    toggleOffline() {
      this.isOffline = !this.isOffline
    },

    simulateIncomingMessage() {
      const id = this.currentSessionId
      if (!id) return
      const now = new Date()
      const time = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`
      const msg: ChatMessage = {
        id: `msg-${Date.now()}`,
        sessionId: id,
        content: '这是一条模拟的测试消息',
        time,
        isSelf: false,
        type: 'text'
      }

      if (!this.messagesBySession[id]) {
        this.messagesBySession[id] = []
      }
      this.messagesBySession[id].push(msg)

      const session = this.sessions.find(s => s.id === id)
      if (session) {
        session.lastMessage = msg.content
        session.time = time
        session.unread = (session.unread || 0) + 1

        if (window.Notification && Notification.permission === 'granted') {
          new Notification(session.name, {
            body: msg.content,
            silent: false
          })
        } else if (window.Notification && Notification.permission !== 'denied') {
          Notification.requestPermission().then(permission => {
            if (permission === 'granted') {
              new Notification(session.name, {
                body: msg.content,
                silent: false
              })
            }
          })
        }
      }
    }
  }
})
