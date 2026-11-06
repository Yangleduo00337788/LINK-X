/**
 * 作者：yangleduo
 */
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as chatApi from '../api/chat'
import type { MessageItem } from '../types/chat'
import { useAppStore } from './app'

function serverMsg(id: number, conversationId = '101'): MessageItem {
  return {
    id: String(id),
    conversationId,
    senderId: '2',
    type: 'text',
    content: `m${id}`,
    createTime: id * 1000,
    isSelf: false
  }
}

function seedLocal(app: ReturnType<typeof useAppStore>, lastId: number, unread = 3) {
  app.isLoggedIn = true
  app.sessions = [
    {
      id: '101',
      name: 'Alice',
      lastMessage: `m${lastId}`,
      time: '00:00',
      avatarText: 'A',
      avatarColor: '#000',
      isReal: true,
      unread
    }
  ]
  app.currentSessionId = '101'
  app.messagesLoaded['101'] = true
  app.messagesBySession['101'] = Array.from({ length: lastId }, (_, i) => ({
    id: String(i + 1),
    sessionId: '101',
    content: `m${i + 1}`,
    time: '00:00',
    createTime: (i + 1) * 1000,
    isSelf: false,
    type: 'text' as const
  }))
}

describe('reconnect HTTP catch-up', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.restoreAllMocks()
  })

  it('pulls pages until the gap is filled (>50 messages)', async () => {
    const app = useAppStore()
    seedLocal(app, 10)

    const listSpy = vi.spyOn(chatApi, 'listMessages').mockImplementation(async (_id, opts) => {
      const after = Number(opts?.after || 0)
      const limit = opts?.limit ?? 50
      const data = Array.from({ length: 120 }, (_, i) => serverMsg(i + 1)).filter(
        m => Number(m.id) > after
      )
      return { code: 200, message: '', data: data.slice(0, limit) }
    })

    await app.pullMessagesAfterCursor('101', '10')

    expect(listSpy).toHaveBeenCalledTimes(3)
    expect(listSpy.mock.calls[0][1]).toMatchObject({ after: '10', limit: 50 })
    expect(listSpy.mock.calls[1][1]).toMatchObject({ after: '60', limit: 50 })
    expect(listSpy.mock.calls[2][1]).toMatchObject({ after: '110', limit: 50 })

    const ids = app.messagesBySession['101'].map(m => m.id)
    expect(ids[0]).toBe('1')
    expect(ids[ids.length - 1]).toBe('120')
    expect(ids).toHaveLength(120)
    expect(new Set(ids).size).toBe(120)
    expect(app.sessions[0].unread).toBe(3)
  })

  it('stops when a full page repeats the same cursor (no infinite loop)', async () => {
    const app = useAppStore()
    seedLocal(app, 10)

    const listSpy = vi.spyOn(chatApi, 'listMessages').mockResolvedValue({
      code: 200,
      message: '',
      data: Array.from({ length: 50 }, (_, i) => serverMsg(i + 11))
    })

    await app.pullMessagesAfterCursor('101', '10')

    expect(listSpy).toHaveBeenCalledTimes(2)
    expect(app.messagesBySession['101']).toHaveLength(60)
  })

  it('catchUpMessagesAfterReconnect uses memory cursor and does not treat history as new unread', async () => {
    const app = useAppStore()
    seedLocal(app, 10, 2)
    app.messagesLoaded['999'] = false

    vi.spyOn(chatApi, 'listMessages').mockImplementation(async (id, opts) => {
      expect(id).toBe('101')
      const after = Number(opts?.after || 0)
      const data = [serverMsg(11), serverMsg(12)].filter(m => Number(m.id) > after)
      return { code: 200, message: '', data }
    })

    await app.catchUpMessagesAfterReconnect()

    const ids = app.messagesBySession['101'].map(m => m.id)
    expect(ids).toEqual(Array.from({ length: 12 }, (_, i) => String(i + 1)))
    expect(app.sessions[0].unread).toBe(2)
    expect(app.sessions[0].lastMessage).toContain('m12')
  })

  it('replaces optimistic local send instead of duplicating after catch-up', async () => {
    const app = useAppStore()
    seedLocal(app, 10)
    app.messagesBySession['101'].push({
      id: 'local-uuid',
      sessionId: '101',
      content: 'pending',
      time: '00:00',
      createTime: 11000,
      isSelf: true,
      type: 'text',
      sendStatus: 'sending',
      clientMsgId: 'local-uuid'
    })

    vi.spyOn(chatApi, 'listMessages').mockResolvedValue({
      code: 200,
      message: '',
      data: [
        {
          ...serverMsg(11),
          senderId: '1',
          isSelf: true,
          content: 'pending',
          clientMsgId: 'local-uuid'
        }
      ]
    })

    await app.pullMessagesAfterCursor('101', '10')

    const list = app.messagesBySession['101']
    expect(list.filter(m => m.content === 'pending')).toHaveLength(1)
    expect(list.some(m => m.id === '11' && m.clientMsgId === 'local-uuid')).toBe(true)
    expect(list.some(m => m.id === 'local-uuid')).toBe(false)
  })
})
