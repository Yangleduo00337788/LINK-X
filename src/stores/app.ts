import { defineStore } from 'pinia'
import type { NavKey, ChatSession, ChatMessage } from '../types'
import { initialSessions, initialMessages } from '../data/mockData'

export interface SendMessageOptions {
  type?: ChatMessage['type']
  replyTo?: ChatMessage
  fileName?: string
  fileSize?: string
  isImage?: boolean
}

export interface SavedLogin {
  username: string
  rememberMe: boolean
  autoLogin: boolean
}

function messagePreview(msg: ChatMessage): string {
  if (msg.type === 'file') return `[文件] ${msg.fileName || msg.content}`
  if (msg.type === 'image' || msg.isImage) return '[图片]'
  return msg.content
}

function nowTime(): string {
  const now = new Date()
  return `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`
}

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
    isLoggedIn: false,
    isLoading: false,
    isOffline: false,
    isLocked: false,
    savedLogin: {
      username: '',
      rememberMe: true,
      autoLogin: false
    } as SavedLogin
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

    sendMessage(content: string, options: SendMessageOptions = {}) {
      const id = this.currentSessionId
      if (!id) return

      const type = options.type ?? 'text'
      const trimmed = content.trim()

      if (type === 'text' && !trimmed) return
      if (type === 'image' && !trimmed) return

      const time = nowTime()
      const isImage = options.isImage ?? type === 'image'

      const msg: ChatMessage = {
        id: `msg-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
        sessionId: id,
        content: type === 'file' ? (options.fileName || trimmed || '文件') : (trimmed || content),
        time,
        isSelf: true,
        type,
        replyTo: options.replyTo,
        fileName: options.fileName,
        fileSize: options.fileSize,
        isImage,
        fileStatus: type === 'file' ? '已发送' : undefined
      }

      if (!this.messagesBySession[id]) {
        this.messagesBySession[id] = []
      }
      this.messagesBySession[id].push(msg)

      const session = this.sessions.find(s => s.id === id)
      if (session) {
        session.lastMessage = messagePreview(msg)
        session.time = time
      }
    },

    recallMessage(messageId: string) {
      const sessionId = this.currentSessionId
      if (!sessionId) return false

      const msgs = this.messagesBySession[sessionId]
      if (!msgs) return false

      const index = msgs.findIndex(m => m.id === messageId)
      if (index === -1) return false

      const wasLast = index === msgs.length - 1
      msgs.splice(index, 1)

      if (wasLast) {
        const session = this.sessions.find(s => s.id === sessionId)
        const last = msgs[msgs.length - 1]
        if (session) {
          session.lastMessage = last ? messagePreview(last) : ''
          if (last) session.time = last.time
        }
      }

      return true
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
      this.isLocked = false
      if (!this.savedLogin.rememberMe) {
        this.savedLogin.username = ''
        this.savedLogin.autoLogin = false
      }
    },

    login(username: string, opts?: { rememberMe?: boolean; autoLogin?: boolean }) {
      const rememberMe = opts?.rememberMe ?? this.savedLogin.rememberMe
      const autoLogin = opts?.autoLogin ?? this.savedLogin.autoLogin

      this.savedLogin.username = rememberMe ? username : ''
      this.savedLogin.rememberMe = rememberMe
      this.savedLogin.autoLogin = autoLogin

      if (username.trim()) {
        this.userProfile.nickname = username.trim()
      }

      this.isLoggedIn = true
      this.isLoading = true
      setTimeout(() => {
        this.isLoading = false
      }, 600)
    },

    tryAutoLogin() {
      if (
        !this.isLoggedIn &&
        this.savedLogin.autoLogin &&
        this.savedLogin.rememberMe &&
        this.savedLogin.username
      ) {
        this.userProfile.nickname = this.savedLogin.username
        this.isLoggedIn = true
      }
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
      const time = nowTime()
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
  },

  persist: {
    key: 'linkx-app',
    paths: [
      'sessions',
      'messagesBySession',
      'currentSessionId',
      'theme',
      'userProfile',
      'isLoggedIn',
      'savedLogin',
      'navKey'
    ]
  }
})
