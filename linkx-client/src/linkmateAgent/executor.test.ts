/**
 * 作者：yangleduo
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  actionStepDelayMs,
  describeLinkMateAction,
  executeLinkMateAction,
  normalizeAgentAction,
  prepareActionForSimulation,
  summarizeAgentRun
} from './executor'
import { setupAgentTestStores } from './test/storeHarness'
import { makeSession, STANDARD_SESSIONS } from './test/fixtures'
import { useAppStore } from '../stores/app'
import { useChatModalsStore } from '../stores/chatModals'
import { useCalendarStore } from '../stores/calendar'
import { useFavoritesStore } from '../stores/favorites'
import type { LinkMateAgentAction, LinkMateAgentToolName } from './types'
import { LINKMATE_AGENT_TOOL_NAMES } from './types'
import * as clientNav from './clientNav'

describe('executor', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    document.body.innerHTML = ''
    setupAgentTestStores({
      sessions: STANDARD_SESSIONS,
      navKey: 'chat',
      currentSession: STANDARD_SESSIONS[0]
    })
  })

  describe('normalizeAgentAction', () => {
    it('resolves relative date for create_calendar_event', () => {
      const action = normalizeAgentAction({
        id: '1',
        name: 'create_calendar_event',
        arguments: { title: '周会', date: '明天' }
      })
      const tomorrow = new Date()
      tomorrow.setDate(tomorrow.getDate() + 1)
      const expected = `${tomorrow.getFullYear()}-${String(tomorrow.getMonth() + 1).padStart(2, '0')}-${String(tomorrow.getDate()).padStart(2, '0')}`
      expect(action.arguments.date).toBe(expected)
      expect(action.arguments.time).toBe('14:00')
      expect(action.arguments.endTime).toBe('15:00')
    })

    it('resolves relative date for update_calendar_event', () => {
      const action = normalizeAgentAction({
        id: '2',
        name: 'update_calendar_event',
        arguments: { eventId: 'e1', date: '明天' }
      })
      const tomorrow = new Date()
      tomorrow.setDate(tomorrow.getDate() + 1)
      const expected = `${tomorrow.getFullYear()}-${String(tomorrow.getMonth() + 1).padStart(2, '0')}-${String(tomorrow.getDate()).padStart(2, '0')}`
      expect(action.arguments.date).toBe(expected)
    })
  })

  describe('executeLinkMateAction', () => {
    it('navigates to a valid panel', async () => {
      const result = await executeLinkMateAction({
        id: 'n1',
        name: 'navigate',
        arguments: { nav: 'calendar' }
      })
      expect(result.ok).toBe(true)
      expect(useAppStore().navKey).toBe('calendar')
    })

    it('rejects invalid navigate target', async () => {
      const result = await executeLinkMateAction({
        id: 'n2',
        name: 'navigate',
        arguments: { nav: 'invalid' }
      })
      expect(result.ok).toBe(false)
    })

    it('opens chat by conversation id', async () => {
      const result = await executeLinkMateAction({
        id: 'c1',
        name: 'open_chat',
        arguments: { conversationId: '101' }
      })
      expect(result.ok).toBe(true)
      expect(useAppStore().currentSessionId).toBe('101')
    })

    it('fails open_chat without target', async () => {
      const result = await executeLinkMateAction({
        id: 'c2',
        name: 'open_chat',
        arguments: {}
      })
      expect(result.ok).toBe(false)
    })

    it('fails open_chat when session not found by name', async () => {
      const result = await executeLinkMateAction({
        id: 'c3',
        name: 'open_chat',
        arguments: { name: '不存在' }
      })
      expect(result.ok).toBe(false)
    })

    it('opens comprehensive search with keyword', async () => {
      const modals = useChatModalsStore()
      const spy = vi.spyOn(modals, 'openComprehensiveSearch')
      const result = await executeLinkMateAction({
        id: 's1',
        name: 'open_search',
        arguments: { keyword: '张三' }
      })
      expect(result.ok).toBe(true)
      expect(spy).toHaveBeenCalledWith('张三')
    })

    it('opens calendar panel', async () => {
      const result = await executeLinkMateAction({
        id: 'cal1',
        name: 'open_calendar',
        arguments: {}
      })
      expect(result.ok).toBe(true)
      expect(useAppStore().navKey).toBe('calendar')
    })

    it('sends message to resolved session', async () => {
      const app = useAppStore()
      const sendSpy = vi.spyOn(app, 'sendMessage').mockResolvedValue(undefined)
      const result = await executeLinkMateAction({
        id: 'm1',
        name: 'send_message',
        arguments: { conversationId: '101', content: '你好' }
      })
      expect(result.ok).toBe(true)
      expect(sendSpy).toHaveBeenCalledWith('你好', { type: 'text' })
    })

    it('rejects empty send_message content', async () => {
      const result = await executeLinkMateAction({
        id: 'm2',
        name: 'send_message',
        arguments: { conversationId: '101', content: '  ' }
      })
      expect(result.ok).toBe(false)
    })

    it('rejects send_message to blocked session', async () => {
      const blocked = makeSession({ id: '999', name: '黑名单', blocked: true })
      setupAgentTestStores({
        sessions: [blocked],
        navKey: 'chat',
        currentSession: blocked
      })
      const result = await executeLinkMateAction({
        id: 'm3',
        name: 'send_message',
        arguments: { conversationId: '999', content: 'hi' }
      })
      expect(result.ok).toBe(false)
    })

    it('creates calendar event via store', async () => {
      const calendar = useCalendarStore()
      vi.spyOn(calendar, 'addEvent').mockResolvedValue('evt-1')
      vi.spyOn(calendar, 'setSelectedDate').mockResolvedValue(undefined)
      const result = await executeLinkMateAction({
        id: 'e1',
        name: 'create_calendar_event',
        arguments: { title: '周会', date: '2026-08-29', time: '14:00', endTime: '15:00' }
      })
      expect(result.ok).toBe(true)
      expect(calendar.addEvent).toHaveBeenCalled()
    })

    it('rejects create_calendar_event without title', async () => {
      const result = await executeLinkMateAction({
        id: 'e2',
        name: 'create_calendar_event',
        arguments: { date: '2026-08-29' }
      })
      expect(result.ok).toBe(false)
    })

    it('rejects create_calendar_event with invalid date', async () => {
      const result = await executeLinkMateAction({
        id: 'e3',
        name: 'create_calendar_event',
        arguments: { title: '周会', date: '明天' }
      })
      expect(result.ok).toBe(false)
    })

    it('adds favorite via store', async () => {
      const favorites = useFavoritesStore()
      vi.spyOn(favorites, 'add').mockResolvedValue(true)
      const result = await executeLinkMateAction({
        id: 'f1',
        name: 'add_favorite',
        arguments: { title: '笔记', content: '内容' }
      })
      expect(result.ok).toBe(true)
      expect(favorites.add).toHaveBeenCalled()
      expect(useAppStore().navKey).toBe('favorites')
    })

    it('rejects send_message when session missing', async () => {
      const result = await executeLinkMateAction({
        id: 'm4',
        name: 'send_message',
        arguments: { content: 'hi' }
      })
      expect(result.ok).toBe(false)
    })

    it('fails create_calendar_event when store returns null', async () => {
      const calendar = useCalendarStore()
      vi.spyOn(calendar, 'addEvent').mockResolvedValue(null)
      const result = await executeLinkMateAction({
        id: 'e4',
        name: 'create_calendar_event',
        arguments: { title: '周会', date: '2026-08-29' }
      })
      expect(result.ok).toBe(false)
    })

    it('fails add_favorite when store returns false', async () => {
      const favorites = useFavoritesStore()
      vi.spyOn(favorites, 'add').mockResolvedValue(false)
      const result = await executeLinkMateAction({
        id: 'f3',
        name: 'add_favorite',
        arguments: { title: '笔记' }
      })
      expect(result.ok).toBe(false)
    })

    it('adds favorite with explicit type', async () => {
      const favorites = useFavoritesStore()
      const addSpy = vi.spyOn(favorites, 'add').mockResolvedValue(true)
      const result = await executeLinkMateAction({
        id: 'f4',
        name: 'add_favorite',
        arguments: { title: '链接', content: 'https://example.com', type: 'link' }
      })
      expect(result.ok).toBe(true)
      expect(addSpy).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'link', title: '链接' })
      )
    })

    it('fails open_chat with unknown conversation id only', async () => {
      const result = await executeLinkMateAction({
        id: 'c5',
        name: 'open_chat',
        arguments: { conversationId: 'ghost' }
      })
      expect(result.ok).toBe(false)
    })

    it('open_search without keyword returns empty message', async () => {
      const result = await executeLinkMateAction({
        id: 's2',
        name: 'open_search',
        arguments: {}
      })
      expect(result.ok).toBe(true)
    })

    it('uiHandled open_search without keyword', async () => {
      const result = await executeLinkMateAction(
        { id: 'uis', name: 'open_search', arguments: {} },
        { uiHandled: true }
      )
      expect(result.ok).toBe(true)
    })

    it('returns uiHandled success without touching stores', async () => {
      const app = useAppStore()
      const setNavSpy = vi.spyOn(app, 'setNav')
      const result = await executeLinkMateAction(
        { id: 'u1', name: 'navigate', arguments: { nav: 'calendar' } },
        { uiHandled: true }
      )
      expect(result.ok).toBe(true)
      expect(setNavSpy).not.toHaveBeenCalled()
    })

    it('catches non-Error throws', async () => {
      vi.spyOn(clientNav, 'applyAgentNav').mockImplementation(() => {
        throw 'plain failure'
      })
      const result = await executeLinkMateAction({
        id: 'err2',
        name: 'navigate',
        arguments: { nav: 'chat' }
      })
      expect(result.ok).toBe(false)
      expect(result.message).toBeTruthy()
    })

    it('opens linkmate panel', async () => {
      vi.spyOn(clientNav, 'openLinkMatePanel').mockResolvedValue(undefined)
      const result = await executeLinkMateAction({
        id: 'lm1',
        name: 'open_linkmate',
        arguments: {}
      })
      expect(result.ok).toBe(true)
      expect(clientNav.openLinkMatePanel).toHaveBeenCalled()
    })

    it('opens contacts with friend-notifs view', async () => {
      vi.spyOn(clientNav, 'applyAgentNav').mockResolvedValue(undefined)
      const result = await executeLinkMateAction({
        id: 'ct1',
        name: 'open_contacts',
        arguments: { view: 'friend-notifs' }
      })
      expect(result.ok).toBe(true)
      expect(useAppStore().contactsActiveView).toBe('friend-notifs')
    })

    it('updates calendar event via store', async () => {
      const calendar = useCalendarStore()
      calendar.events = [{ id: 'e1', title: '周会', date: '2026-08-29' }]
      vi.spyOn(calendar, 'updateEvent').mockResolvedValue(true)
      const result = await executeLinkMateAction({
        id: 'ue1',
        name: 'update_calendar_event',
        arguments: { eventId: 'e1', title: '新周会' }
      })
      expect(result.ok).toBe(true)
      expect(calendar.updateEvent).toHaveBeenCalled()
    })

    it('deletes calendar event via store', async () => {
      const calendar = useCalendarStore()
      calendar.events = [{ id: 'e1', title: '周会', date: '2026-08-29' }]
      vi.spyOn(calendar, 'removeEvent').mockResolvedValue(true)
      const result = await executeLinkMateAction({
        id: 'de1',
        name: 'delete_calendar_event',
        arguments: { eventId: 'e1' }
      })
      expect(result.ok).toBe(true)
      expect(calendar.removeEvent).toHaveBeenCalled()
    })
  })

  describe('executeLinkMateAction uiHandled branches', () => {
    const UI_HANDLED_ARGS: Partial<Record<LinkMateAgentToolName, Record<string, unknown>>> = {
      navigate: { nav: 'calendar' },
      open_chat: { conversationId: '101' },
      send_message: { conversationId: '101', content: '你好' },
      open_search: { keyword: '测试' },
      create_calendar_event: { title: '周会', date: '2026-08-29' },
      update_calendar_event: { eventId: 'e1', title: '新周会' },
      delete_calendar_event: { eventId: 'e1' },
      add_favorite: { title: '笔记' },
      update_favorite: { favoriteId: 'f1', title: '新标题' },
      delete_favorite: { favoriteId: 'f1' },
      tag_favorite: { favoriteId: 'f1', tags: ['工作'] },
      add_friend: { username: 'testuser' },
      handle_friend_request: { fromName: '王五', action: 'accept' },
      handle_group_invitation: { groupName: '项目群', action: 'accept' },
      create_folder: { name: '资料' },
      publish_moment: { content: '动态' },
      send_red_packet: { conversationId: '101', amount: '8.88' },
      start_call: { conversationId: '101', callType: 'voice' },
      create_group: { groupName: '新群', memberNames: ['张三'] },
      add_group_members: { groupName: '项目群', memberNames: ['李四'] },
      update_setting: { key: 'soundNotify', value: 'false' },
      recharge_balance: { amount: '100' },
      open_contacts: { view: 'default' }
    }

    it.each(
      LINKMATE_AGENT_TOOL_NAMES.map(
        name => [name, UI_HANDLED_ARGS[name] ?? {}] as const
      )
    )('returns success for %s via uiHandled', async (name, arguments_) => {
      const result = await executeLinkMateAction(
        { id: `ui-${name}`, name, arguments: arguments_ },
        { uiHandled: true }
      )
      expect(result.ok).toBe(true)
      expect(result.message).toBeTruthy()
    })

    it('open_chat uiHandled uses session name when resolved', async () => {
      const result = await executeLinkMateAction(
        { id: 'ui-oc', name: 'open_chat', arguments: { conversationId: '101' } },
        { uiHandled: true }
      )
      expect(result.ok).toBe(true)
      expect(result.message).toContain('张三')
    })

    it('open_chat uiHandled uses conversationId when session missing', async () => {
      setupAgentTestStores({ sessions: [], navKey: 'chat', currentSession: null })
      const result = await executeLinkMateAction(
        { id: 'ui-oc2', name: 'open_chat', arguments: { conversationId: '404' } },
        { uiHandled: true }
      )
      expect(result.ok).toBe(true)
      expect(result.message).toContain('404')
    })

    it('falls back to runAllDone for unknown uiHandled action', async () => {
      const result = await executeLinkMateAction(
        {
          id: 'ui-unknown',
          name: 'not_a_real_tool' as LinkMateAgentToolName,
          arguments: {}
        },
        { uiHandled: true }
      )
      expect(result.ok).toBe(true)
    })
  })

  describe('executeLinkMateAction errors', () => {
    it('handles unknown action name', async () => {
      const result = await executeLinkMateAction({
        id: 'x1',
        name: 'unknown_tool' as LinkMateAgentToolName,
        arguments: {}
      })
      expect(result.ok).toBe(false)
    })

    it('catches executor errors', async () => {
      vi.spyOn(clientNav, 'applyAgentNav').mockImplementation(() => {
        throw new Error('boom')
      })
      const result = await executeLinkMateAction({
        id: 'err1',
        name: 'navigate',
        arguments: { nav: 'chat' }
      })
      expect(result.ok).toBe(false)
      expect(result.message).toBe('boom')
    })
  })

  describe('describeLinkMateAction', () => {
    const cases: LinkMateAgentAction[] = [
      { id: '1', name: 'navigate', arguments: { nav: 'chat' } },
      { id: '2', name: 'open_chat', arguments: { name: '张三' } },
      { id: '3', name: 'open_search', arguments: { keyword: '测试' } },
      { id: '4', name: 'send_message', arguments: { name: '张三', content: '你好世界' } },
      { id: '5', name: 'create_calendar_event', arguments: { title: '周会', date: '2026-08-29' } },
      { id: '6', name: 'add_favorite', arguments: { title: '收藏' } },
      { id: '7', name: 'open_calendar', arguments: {} },
      { id: '8', name: 'open_linkmate', arguments: {} },
      { id: '9', name: 'open_contacts', arguments: { view: 'friend-notifs' } },
      { id: '10', name: 'update_calendar_event', arguments: { eventId: 'e1', title: '新周会' } },
      { id: '11', name: 'delete_calendar_event', arguments: { eventId: 'e1' } },
      { id: '12', name: 'add_friend', arguments: { username: 'testuser' } },
      { id: '13', name: 'create_folder', arguments: { name: '资料' } },
      { id: '14', name: 'publish_moment', arguments: { content: '今天天气不错' } },
      { id: '15', name: 'send_red_packet', arguments: { amount: '8.88' } },
      { id: '16', name: 'update_setting', arguments: { key: 'soundNotify' } },
      { id: '17', name: 'recharge_balance', arguments: { amount: '100' } }
    ]

    for (const action of cases) {
      it(`describes ${action.name}`, () => {
        expect(describeLinkMateAction(action)).toBeTruthy()
      })
    }

    it('describes send_message without content preview', () => {
      const label = describeLinkMateAction({
        id: 'sm',
        name: 'send_message',
        arguments: { name: '张三', content: '' }
      })
      expect(label).toContain('张三')
    })

    it('describes create_calendar_event with title only', () => {
      const label = describeLinkMateAction({
        id: 'ce',
        name: 'create_calendar_event',
        arguments: { title: '周会' }
      })
      expect(label).toContain('周会')
    })

    it('describes navigate without nav key', () => {
      expect(describeLinkMateAction({ id: 'n', name: 'navigate', arguments: {} })).toBeTruthy()
    })

    it('describes open_chat without target name', () => {
      expect(describeLinkMateAction({ id: 'c', name: 'open_chat', arguments: {} })).toBeTruthy()
    })

    it('describes open_search without keyword', () => {
      expect(describeLinkMateAction({ id: 's', name: 'open_search', arguments: {} })).toBeTruthy()
    })

    it('describes send_message with conversationId target', () => {
      const label = describeLinkMateAction({
        id: 'sm2',
        name: 'send_message',
        arguments: { conversationId: '101', content: 'hi' }
      })
      expect(label).toContain('101')
    })

    it('describes send_message using current chat when target missing', () => {
      const label = describeLinkMateAction({
        id: 'sm3',
        name: 'send_message',
        arguments: { content: 'hi' }
      })
      expect(label).toBeTruthy()
    })

    it('describes add_favorite without title', () => {
      expect(describeLinkMateAction({ id: 'f', name: 'add_favorite', arguments: {} })).toBeTruthy()
    })
  })

  describe('summarizeAgentRun', () => {
    it('returns empty for no completed actions', () => {
      expect(summarizeAgentRun([])).toBe('')
    })

    it('returns last result message when present', () => {
      const action: LinkMateAgentAction = { id: '1', name: 'navigate', arguments: { nav: 'chat' } }
      const summary = summarizeAgentRun([
        { action, result: { ok: true, message: 'done' } }
      ])
      expect(summary).toBe('done')
    })

    it('returns partial summary when some actions fail', () => {
      const action: LinkMateAgentAction = { id: '1', name: 'navigate', arguments: { nav: 'chat' } }
      const summary = summarizeAgentRun([
        { action, result: { ok: true } },
        { action, result: { ok: false } }
      ])
      expect(summary).toBeTruthy()
    })

    it('returns runAllDone when all succeed without messages', () => {
      const action: LinkMateAgentAction = { id: '1', name: 'navigate', arguments: { nav: 'chat' } }
      const summary = summarizeAgentRun([
        { action, result: { ok: true } },
        { action, result: { ok: true } }
      ])
      expect(summary).toBeTruthy()
    })
  })

  describe('prepareActionForSimulation', () => {
    it('completes for chat-related actions', async () => {
      await expect(
        prepareActionForSimulation({
          id: 'p1',
          name: 'open_chat',
          arguments: { conversationId: '101' }
        })
      ).resolves.toBeUndefined()
    })
  })

  it('actionStepDelayMs returns positive delay', () => {
    expect(actionStepDelayMs()).toBeGreaterThan(0)
  })
})
