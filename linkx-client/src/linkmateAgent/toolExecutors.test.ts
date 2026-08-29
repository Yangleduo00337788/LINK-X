/**
 * 作者：yangleduo
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAppStore } from '../stores/app'
import { useCalendarStore } from '../stores/calendar'
import { useContactsStore } from '../stores/contacts'
import { useFavoritesStore } from '../stores/favorites'
import { useNotificationsStore } from '../stores/notifications'
import { useDriveStore } from '../stores/drive'
import { useMomentsStore } from '../stores/moments'
import * as friendApi from '../api/friend'
import { INVITE_STATUS } from '../types/inviteStatus'
import { makeSession, STANDARD_SESSIONS } from './test/fixtures'
import { setupAgentTestStores } from './test/storeHarness'
import {
  executeAddFriend,
  executeCreateFolder,
  executeDeleteCalendarEvent,
  executeHandleFriendRequest,
  executeOpenContacts,
  executeOpenLinkmate,
  executePublishMoment,
  executeSendMessage,
  executeUpdateCalendarEvent,
  executeUpdateFavorite
} from './toolExecutors'
import * as clientNav from './clientNav'

describe('toolExecutors', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    setupAgentTestStores({
      sessions: STANDARD_SESSIONS,
      navKey: 'chat',
      currentSession: STANDARD_SESSIONS[0]
    })
    const app = useAppStore()
    vi.spyOn(app, 'loadSessionMessages').mockResolvedValue(undefined)
    vi.spyOn(app, 'loadSessionDraft').mockResolvedValue('')
    vi.spyOn(app, 'hydrateSessionFromLocalDb').mockResolvedValue(false)
    vi.spyOn(app, 'reportSessionRead').mockResolvedValue(undefined)
    vi.spyOn(useDriveStore(), 'refreshAll').mockResolvedValue(undefined)
    vi.spyOn(clientNav, 'applyAgentNav').mockResolvedValue(undefined)
    vi.spyOn(clientNav, 'openLinkMatePanel').mockResolvedValue(undefined)
  })

  it('executeOpenLinkmate returns success', async () => {
    const result = await executeOpenLinkmate()
    expect(result.ok).toBe(true)
    expect(clientNav.openLinkMatePanel).toHaveBeenCalled()
  })

  it('executeOpenContacts sets contacts view', async () => {
    const result = await executeOpenContacts({ view: 'friend-notifs' })
    expect(result.ok).toBe(true)
    expect(clientNav.applyAgentNav).toHaveBeenCalledWith('contacts')
    expect(useAppStore().contactsActiveView).toBe('friend-notifs')
  })

  it('executeSendMessage with mentionNames and replyToMessageId', async () => {
    const app = useAppStore()
    app.messagesBySession['101'] = [
      {
        id: 'msg-1',
        sessionId: '101',
        content: '原消息',
        time: '12:00',
        isSelf: false,
        type: 'text'
      }
    ]
    const sendSpy = vi.spyOn(app, 'sendMessage').mockResolvedValue(undefined)
    const result = await executeSendMessage({
      conversationId: '101',
      content: '回复你',
      mentionNames: ['李四'],
      replyToMessageId: 'msg-1'
    })
    expect(result.ok).toBe(true)
    expect(sendSpy).toHaveBeenCalledWith(
      '@李四 回复你',
      expect.objectContaining({
        type: 'text',
        replyTo: expect.objectContaining({ id: 'msg-1' })
      })
    )
  })

  it('executeSendMessage fails when reply target missing', async () => {
    const result = await executeSendMessage({
      conversationId: '101',
      content: '回复',
      replyToMessageId: 'missing'
    })
    expect(result.ok).toBe(false)
  })

  it('executeAddFriend calls friend API', async () => {
    vi.spyOn(friendApi, 'sendFriendRequest').mockResolvedValue({ code: 200, data: null, message: 'ok' })
    vi.spyOn(useNotificationsStore(), 'fetchFriendRequests').mockResolvedValue(undefined)
    vi.spyOn(useContactsStore(), 'fetchFriends').mockResolvedValue(undefined)
    const result = await executeAddFriend({ username: 'testuser' })
    expect(result.ok).toBe(true)
  })

  it('executeHandleFriendRequest accepts by fromName', async () => {
    const notifications = useNotificationsStore()
    notifications.friendNotifs = [
      {
        id: 'r1',
        requestId: 'r1',
        fromUserId: 'u1',
        peerUserId: 'u1',
        direction: 'incoming',
        avatar: '',
        name: '王五',
        action: '',
        date: '',
        createTime: '',
        message: '',
        source: '',
        status: INVITE_STATUS.PENDING
      }
    ]
    const acceptSpy = vi.spyOn(notifications, 'acceptFriendRequest').mockResolvedValue(undefined)
    const result = await executeHandleFriendRequest({ fromName: '王五', action: 'accept' })
    expect(result.ok).toBe(true)
    expect(acceptSpy).toHaveBeenCalledWith('r1')
  })

  it('executeUpdateCalendarEvent patches event', async () => {
    const calendar = useCalendarStore()
    calendar.events = [{ id: 'e1', title: '周会', date: '2026-08-29' }]
    vi.spyOn(calendar, 'updateEvent').mockResolvedValue(true)
    const result = await executeUpdateCalendarEvent({
      eventId: 'e1',
      title: '新周会'
    })
    expect(result.ok).toBe(true)
    expect(calendar.updateEvent).toHaveBeenCalled()
  })

  it('executeDeleteCalendarEvent removes event', async () => {
    const calendar = useCalendarStore()
    calendar.events = [{ id: 'e1', title: '周会', date: '2026-08-29' }]
    vi.spyOn(calendar, 'removeEvent').mockResolvedValue(true)
    const result = await executeDeleteCalendarEvent({ eventId: 'e1' })
    expect(result.ok).toBe(true)
  })

  it('executeUpdateFavorite updates item', async () => {
    const favorites = useFavoritesStore()
    favorites.items = [
      {
        id: 'f1',
        title: '旧标题',
        content: '旧内容',
        preview: '旧内容',
        type: 'note',
        time: '12:00'
      }
    ]
    vi.spyOn(favorites, 'update').mockResolvedValue(true)
    const result = await executeUpdateFavorite({ favoriteId: 'f1', title: '新标题' })
    expect(result.ok).toBe(true)
  })

  it('executeCreateFolder calls drive store', async () => {
    vi.spyOn(useDriveStore(), 'createFolder').mockResolvedValue({
      id: 'folder-1',
      kind: 'folder',
      name: '资料'
    })
    const result = await executeCreateFolder({ name: '资料' })
    expect(result.ok).toBe(true)
    expect(clientNav.applyAgentNav).toHaveBeenCalledWith('files')
  })

  it('executeSendMessage blocked session', async () => {
    const blocked = makeSession({ id: '999', name: '黑名单', blocked: true })
    setupAgentTestStores({ sessions: [blocked], navKey: 'chat', currentSession: blocked })
    const result = await executeSendMessage({ conversationId: '999', content: 'hi' })
    expect(result.ok).toBe(false)
  })

  it('publish_moment delegates to moments store', async () => {
    vi.spyOn(useMomentsStore(), 'addPost').mockResolvedValue({ ok: true as const })
    const result = await executePublishMoment({ content: '今天天气不错' })
    expect(result.ok).toBe(true)
    expect(clientNav.applyAgentNav).toHaveBeenCalledWith('moments')
  })
})
