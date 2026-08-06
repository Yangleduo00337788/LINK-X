import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAppStore } from './app'

// Mock dependencies（路径相对本测试文件所在 stores/）
vi.mock('../api/chat', () => ({
  listSessions: vi.fn(),
  listMessages: vi.fn(),
  openPrivateChat: vi.fn(),
  togglePin: vi.fn(async () => ({ code: 200, data: true, message: 'ok' })),
}))

vi.mock('../utils/chatSocket', () => ({
  connectChatSocket: vi.fn(),
  disconnectChatSocket: vi.fn(),
  sendChatMessage: vi.fn(),
  isChatSocketConnected: vi.fn(() => true)
}))

vi.mock('../utils/tokenStorage', () => ({
  getToken: vi.fn(() => Promise.resolve('mock-token')),
  getRefreshToken: vi.fn(() => Promise.resolve(null)),
  hasRefreshToken: vi.fn(() => Promise.resolve(false)),
  isWebEnvironment: vi.fn(() => false),
  saveTokenPair: vi.fn(),
  clearTokens: vi.fn()
}))

vi.mock('./appSettings', () => ({
  useAppSettingsStore: () => ({ privacySendReadReceipt: true })
}))

describe('useAppStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('should have default state', () => {
    const store = useAppStore()
    expect(store.navKey).toBe('chat')
    expect(store.isLoggedIn).toBe(false)
    expect(store.isLocked).toBe(false)
    expect(store.theme).toBe('light')
  })

  it('should set navigation key', () => {
    const store = useAppStore()
    store.setNav('contacts')
    expect(store.navKey).toBe('contacts')
  })

  it('should toggle theme', () => {
    const store = useAppStore()
    expect(store.theme).toBe('light')
    store.toggleTheme()
    expect(store.theme).toBe('dark')
    store.toggleTheme()
    expect(store.theme).toBe('light')
  })

  it('should lock and unlock', () => {
    const store = useAppStore()
    expect(store.isLocked).toBe(false)
    store.lock()
    expect(store.isLocked).toBe(true)
    store.unlock()
    expect(store.isLocked).toBe(false)
  })

  it('should manage sessions', () => {
    const store = useAppStore()
    const session = {
      id: 'test-session',
      name: 'Test Chat',
      lastMessage: '',
      time: '12:00',
      avatarText: 'T',
      avatarColor: '#12b7f5',
      isGroup: false
    }

    store.ensureSession(session)
    expect(store.sessions).toContainEqual(expect.objectContaining({ id: 'test-session' }))
    expect(store.currentSessionId).toBe('test-session')
  })

  it('should delete session', () => {
    const store = useAppStore()
    store.sessions = [
      { id: 's1', name: 'Chat 1' } as any,
      { id: 's2', name: 'Chat 2' } as any
    ]
    store.currentSessionId = 's1'
    store.deleteSession('s1')
    expect(store.sessions).toHaveLength(1)
    expect(store.sessions[0].id).toBe('s2')
  })

  it('should toggle session pin', async () => {
    const store = useAppStore()
    store.sessions = [{ id: 's1', pinned: false } as any]
    await store.toggleSessionPin('s1')
    expect(store.sessions[0].pinned).toBe(true)
    await store.toggleSessionPin('s1')
    expect(store.sessions[0].pinned).toBe(false)
  })

  it('handleReadReceipt 单聊应标记已读', () => {
    const store = useAppStore()
    store.sessions = [{ id: 'c1', isGroup: false } as any]
    store.messagesBySession['c1'] = [
      { id: '100', isSelf: true, sendStatus: 'delivered' } as any,
      { id: '200', isSelf: true, sendStatus: 'sent' } as any
    ]
    store.handleReadReceipt({
      conversationId: 'c1',
      readerId: '2',
      lastReadMessageId: '200'
    })
    expect(store.messagesBySession['c1'][0].sendStatus).toBe('read')
    expect(store.messagesBySession['c1'][1].sendStatus).toBe('read')
  })

  it('handleReadReceipt 群聊应递增已读人数而非标已读', () => {
    const store = useAppStore()
    store.sessions = [{ id: 'g1', isGroup: true } as any]
    store.messagesBySession['g1'] = [
      { id: '100', isSelf: true, sendStatus: 'sent', readCount: 1, totalMembers: 5 } as any
    ]
    store.handleReadReceipt({
      conversationId: 'g1',
      readerId: '9',
      lastReadMessageId: '100'
    })
    expect(store.messagesBySession['g1'][0].readCount).toBe(2)
    expect(store.messagesBySession['g1'][0].sendStatus).toBe('sent')
  })

  it('handleDeliveryReceipt 应标记已送达', () => {
    const store = useAppStore()
    store.messagesBySession['c1'] = [
      { id: '55', isSelf: true, sendStatus: 'sent' } as any
    ]
    store.handleDeliveryReceipt({ conversationId: 'c1', messageId: '55' })
    expect(store.messagesBySession['c1'][0].sendStatus).toBe('delivered')
  })
})
