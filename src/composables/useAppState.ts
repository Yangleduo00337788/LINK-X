import { ref, computed } from 'vue'
import type { NavKey, ChatSession, ChatMessage } from '../types'
import { initialSessions, initialMessages } from '../data/mockData'

const navKey = ref<NavKey>('chat')
const sessions = ref<ChatSession[]>([...initialSessions])
const messagesBySession = ref<Record<string, ChatMessage[]>>({ ...initialMessages })
const currentSessionId = ref<string | null>(null)
const theme = ref<'light' | 'dark'>('light')
const contactsActiveView = ref<'none' | 'friend-notifs' | 'group-notifs'>('none')

const userProfile = ref({
  nickname: '晚香玉',
  signature: '编辑个性签名'
})

const isLoggedIn = ref(true)

export function useAppState() {
  const currentSession = computed(() =>
    sessions.value.find(s => s.id === currentSessionId.value) ?? null
  )

  const currentMessages = computed(() => {
    const id = currentSessionId.value
    if (!id) return []
    return messagesBySession.value[id] ?? []
  })

  function setNav(key: NavKey) {
    navKey.value = key
  }

  function selectSession(session: ChatSession) {
    currentSessionId.value = session.id
    const s = sessions.value.find(x => x.id === session.id)
    if (s?.unread) {
      s.unread = 0
    }
    if (!messagesBySession.value[session.id]) {
      messagesBySession.value[session.id] = []
    }
  }

  function ensureSession(session: ChatSession) {
    const exists = sessions.value.find(s => s.id === session.id)
    if (!exists) {
      sessions.value.unshift(session)
    }
    selectSession(session)
    navKey.value = 'chat'
  }

  function sendMessage(text: string, type: ChatMessage['type'] = 'text') {
    const id = currentSessionId.value
    if (!id || !text.trim()) return
    const now = new Date()
    const time = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`
    const msg: ChatMessage = {
      id: `msg-${Date.now()}`,
      sessionId: id,
      content: text.trim(),
      time,
      isSelf: true,
      type
    }
    if (!messagesBySession.value[id]) {
      messagesBySession.value[id] = []
    }
    messagesBySession.value[id].push(msg)
    const session = sessions.value.find(s => s.id === id)
    if (session) {
      session.lastMessage = text.trim()
      session.time = time
    }
  }

  function toggleTheme() {
    theme.value = theme.value === 'light' ? 'dark' : 'light'
    document.documentElement.setAttribute('data-theme', theme.value)
  }

  function updateSignature(text: string) {
    userProfile.value.signature = text
  }

  function updateNickname(name: string) {
    userProfile.value.nickname = name
  }

  function logout() {
    isLoggedIn.value = false
  }

  function login() {
    isLoggedIn.value = true
  }

  return {
    navKey,
    sessions,
    messagesBySession,
    currentSessionId,
    currentSession,
    currentMessages,
    theme,
    userProfile,
    contactsActiveView,
    isLoggedIn,
    setNav,
    selectSession,
    ensureSession,
    sendMessage,
    toggleTheme,
    updateSignature,
    updateNickname,
    logout,
    login
  }
}