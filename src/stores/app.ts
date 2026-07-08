import { defineStore } from 'pinia'
import type { NavKey, ChatSession, ChatMessage, ContactItem } from '../types'
import { initialSessions, initialMessages, sessionFromContact } from '../data/mockData'
import { useContactsStore } from './contacts'
import { useGroupMetaStore } from './groupMeta'

export interface SendMessageOptions {
  type?: ChatMessage['type']
  replyTo?: ChatMessage
  fileName?: string
  fileSize?: string
  isImage?: boolean
}

export interface SavedLogin {
  username: string
  password: string
  rememberMe: boolean
  autoLogin: boolean
}

export interface CreateGroupMember {
  id: string
  name: string
  avatarText: string
  avatarColor: string
}

const GROUP_COLORS = ['#12b7f5', '#52c41a', '#722ed1', '#fa8c16', '#eb2f96', '#13c2c2']

function messagePreview(msg: ChatMessage): string {
  if (msg.type === 'file') return `[文件] ${msg.fileName || msg.content}`
  if (msg.type === 'image' || msg.isImage) return '[图片]'
  return msg.content
}

function nowTime(): string {
  const now = new Date()
  return `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`
}

function pickGroupColor(seed: string): string {
  let hash = 0
  for (let i = 0; i < seed.length; i++) hash += seed.charCodeAt(i)
  return GROUP_COLORS[hash % GROUP_COLORS.length]
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
      password: '',
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
    },
    sortedSessions(state): ChatSession[] {
      return [...state.sessions].sort((a, b) => {
        if (a.pinned && !b.pinned) return -1
        if (!a.pinned && b.pinned) return 1
        return 0
      })
    },
    groupSessions(state): ChatSession[] {
      return state.sessions.filter(s => s.isGroup)
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
      const exists =
        this.sessions.find(s => s.id === session.id) ??
        (!session.isGroup
          ? this.sessions.find(s => !s.isGroup && s.name === session.name)
          : undefined)
      if (exists) {
        this.selectSession(exists)
        this.navKey = 'chat'
        return exists
      }
      this.sessions.unshift(session)
      this.selectSession(session)
      this.navKey = 'chat'
      return session
    },

    startChatWithContact(contact: ContactItem) {
      const existing = this.sessions.find(
        s => !s.isGroup && (s.id === contact.id || s.name === contact.name)
      )
      if (existing) {
        this.selectSession(existing)
        this.navKey = 'chat'
        return
      }
      this.ensureSession(sessionFromContact(contact))
    },

    createGroup(members: CreateGroupMember[], groupName?: string) {
      if (members.length === 0) return null
      const id = `group-${Date.now()}`
      const time = nowTime()
      const name =
        groupName?.trim() ||
        (members.length <= 2
          ? members.map(m => m.name).join('、')
          : `群聊（${members.length + 1}人）`)
      const session: ChatSession = {
        id,
        name,
        lastMessage: '系统：欢迎加入群聊',
        time,
        avatarText: name.charAt(0) || '群',
        avatarColor: pickGroupColor(name),
        isGroup: true
      }
      this.messagesBySession[id] = [
        {
          id: `msg-sys-${Date.now()}`,
          sessionId: id,
          content: `系统：${this.userProfile.nickname} 发起了群聊`,
          time,
          isSelf: false,
          type: 'system'
        }
      ]
      this.ensureSession(session)
      return session
    },

    joinGroup(groupName: string) {
      const exists = this.sessions.find(s => s.isGroup && s.name === groupName)
      if (exists) {
        this.selectSession(exists)
        this.navKey = 'chat'
        return exists
      }
      const id = `group-join-${Date.now()}`
      const time = nowTime()
      const session: ChatSession = {
        id,
        name: groupName,
        lastMessage: '系统：欢迎加入群聊',
        time,
        avatarText: groupName.charAt(0) || '群',
        avatarColor: pickGroupColor(groupName),
        isGroup: true
      }
      this.messagesBySession[id] = [
        {
          id: `msg-sys-${Date.now()}`,
          sessionId: id,
          content: '系统：你已加入群聊',
          time,
          isSelf: false,
          type: 'system'
        }
      ]
      this.ensureSession(session)
      useContactsStore().syncFriendFromSession(session)
      return session
    },

    addFriendSession(name: string) {
      const id = `friend-${Date.now()}`
      const time = nowTime()
      const session: ChatSession = {
        id,
        name,
        lastMessage: '我们已经是好友了，开始聊天吧',
        time,
        avatarText: name.charAt(0) || '?',
        avatarColor: pickGroupColor(name),
        online: true
      }
      this.messagesBySession[id] = [
        {
          id: `msg-sys-${Date.now()}`,
          sessionId: id,
          content: '系统：你们已成为好友',
          time,
          isSelf: false,
          type: 'system'
        }
      ]
      this.ensureSession(session)
      useContactsStore().syncFriendFromSession(session)
      return session
    },

    toggleSessionPin(sessionId: string) {
      const s = this.sessions.find(x => x.id === sessionId)
      if (s) s.pinned = !s.pinned
    },

    toggleSessionMute(sessionId: string) {
      const s = this.sessions.find(x => x.id === sessionId)
      if (s) s.muted = !s.muted
    },

    deleteSession(sessionId: string) {
      this.sessions = this.sessions.filter(s => s.id !== sessionId)
      delete this.messagesBySession[sessionId]
      if (this.currentSessionId === sessionId) {
        this.currentSessionId = this.sessions[0]?.id ?? null
      }
    },

    clearSessionMessages(sessionId: string) {
      this.messagesBySession[sessionId] = []
      const session = this.sessions.find(s => s.id === sessionId)
      if (session) {
        session.lastMessage = ''
      }
    },

    toggleSessionBlock(sessionId: string) {
      const s = this.sessions.find(x => x.id === sessionId)
      if (s) s.blocked = !s.blocked
    },

    inviteGroupMembers(sessionId: string, names: string[]) {
      if (!names.length) return
      useGroupMetaStore().addMembers(sessionId, names)
      const time = nowTime()
      const text = `系统：${this.userProfile.nickname} 邀请了 ${names.join('、')} 加入群聊`
      if (!this.messagesBySession[sessionId]) {
        this.messagesBySession[sessionId] = []
      }
      this.messagesBySession[sessionId].push({
        id: `msg-sys-${Date.now()}`,
        sessionId,
        content: text,
        time,
        isSelf: false,
        type: 'system'
      })
      const session = this.sessions.find(s => s.id === sessionId)
      if (session) {
        session.lastMessage = text
        session.time = time
      }
    },

    leaveGroup(sessionId: string) {
      this.deleteSession(sessionId)
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
        const last = msgs.filter(m => m.type !== 'system').pop() ?? msgs[msgs.length - 1]
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
        this.savedLogin.password = ''
        this.savedLogin.autoLogin = false
      }
    },

    login(username: string, password: string, opts?: { rememberMe?: boolean; autoLogin?: boolean }) {
      const rememberMe = opts?.rememberMe ?? this.savedLogin.rememberMe
      const autoLogin = opts?.autoLogin ?? this.savedLogin.autoLogin

      this.savedLogin.username = rememberMe ? username : ''
      this.savedLogin.password = rememberMe ? password : ''
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

    verifyLockPassword(password: string): boolean {
      const expected = this.savedLogin.password || '123456'
      return password === expected
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
        if (!session.muted) {
          session.unread = (session.unread || 0) + 1
        }

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
      'navKey',
      'isOffline'
    ]
  }
})
