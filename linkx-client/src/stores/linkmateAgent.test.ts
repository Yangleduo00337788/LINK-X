/**
 * 作者：yangleduo
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import * as cursorSim from '../linkmateAgent/cursorSim'
import * as executor from '../linkmateAgent/executor'
import * as uiBridge from '../linkmateAgent/uiBridge'
import { STANDARD_SESSIONS } from '../linkmateAgent/test/fixtures'
import { setupAgentTestStores } from '../linkmateAgent/test/storeHarness'
import { useLinkMateAgentStore } from './linkmateAgent'

describe('linkmateAgent store', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    localStorage.clear()
    setActivePinia(createPinia())
    setupAgentTestStores({
      sessions: STANDARD_SESSIONS,
      navKey: 'chat',
      currentSession: STANDARD_SESSIONS[0]
    })

    vi.spyOn(cursorSim, 'simulateActionCursor').mockResolvedValue({ uiHandled: true })
    vi.spyOn(executor, 'executeLinkMateAction').mockResolvedValue({ ok: true, message: 'done' })
    vi.spyOn(executor, 'prepareActionForSimulation').mockResolvedValue(undefined)
    vi.spyOn(executor, 'actionStepDelayMs').mockReturnValue(0)
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
    localStorage.clear()
  })

  it('toggles and persists agent mode', () => {
    const store = useLinkMateAgentStore()
    expect(store.agentMode).toBe(false)
    store.toggleAgentMode()
    expect(store.agentMode).toBe(true)
    expect(localStorage.getItem('linkx-linkmate-agent-mode')).toBe('1')
    store.setAgentMode(false)
    expect(localStorage.getItem('linkx-linkmate-agent-mode')).toBe('0')
  })

  it('buildClientContext includes nav, session and todayDate', () => {
    const store = useLinkMateAgentStore()
    const ctx = store.buildClientContext()
    expect(ctx.currentNav).toBe('chat')
    expect(ctx.currentSessionId).toBe('101')
    expect(ctx.currentSessionTitle).toBe('张三')
    expect(ctx.todayDate).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(ctx.recentSessions).toContain('张三')
  })

  it('exposes getters for run state', () => {
    const store = useLinkMateAgentStore()
    store.beginPlanning()
    store.previewToolCall({
      id: 'p1',
      name: 'navigate',
      arguments: '{"nav":"calendar"}'
    })
    expect(store.isPlanning).toBe(true)
    expect(store.isActive).toBe(true)
    expect(store.plannedStepLabels.length).toBe(1)
  })

  it('runActions executes navigate without confirm', async () => {
    const store = useLinkMateAgentStore()
    const promise = store.runActions([
      { id: 'a1', name: 'navigate', arguments: { nav: 'calendar' } }
    ])
    await vi.runAllTimersAsync()
    await promise

    expect(store.run.phase).toBe('idle')
    expect(store.run.completed).toHaveLength(1)
    expect(store.run.completed[0].result.ok).toBe(true)
    expect(cursorSim.simulateActionCursor).toHaveBeenCalled()
  })

  it('runActions waits for confirm on send_message and approves', async () => {
    const store = useLinkMateAgentStore()
    const promise = store.runActions([
      {
        id: 'm1',
        name: 'send_message',
        arguments: { conversationId: '101', content: '你好' }
      }
    ])

    await Promise.resolve()
    expect(store.run.phase).toBe('confirming')
    store.approvePendingConfirm()
    await vi.runAllTimersAsync()
    await promise

    expect(store.run.completed).toHaveLength(1)
    expect(store.run.completed[0].result.ok).toBe(true)
  })

  it('runActions records rejection for confirm-required action', async () => {
    const store = useLinkMateAgentStore()
    const promise = store.runActions([
      {
        id: 'e1',
        name: 'create_calendar_event',
        arguments: { title: '周会', date: '2026-08-29' }
      }
    ])

    await Promise.resolve()
    store.rejectPendingConfirm()
    await vi.runAllTimersAsync()
    await promise

    expect(store.run.completed).toHaveLength(1)
    expect(store.run.completed[0].result.ok).toBe(false)
  })

  it('runActions runs multi-step queue with normalized dates', async () => {
    const store = useLinkMateAgentStore()
    vi.spyOn(store, 'waitForConfirm').mockResolvedValue(true)
    const promise = store.runActions([
      { id: 'n1', name: 'navigate', arguments: { nav: 'chat' } },
      {
        id: 'e1',
        name: 'create_calendar_event',
        arguments: { title: '周会', date: '明天' }
      }
    ])

    await vi.runAllTimersAsync()
    await promise

    expect(store.run.completed).toHaveLength(2)
    const eventAction = store.run.completed[1].action
    expect(eventAction.arguments.date).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it('cancelRun stops pending confirm and resets phase', async () => {
    const store = useLinkMateAgentStore()
    const promise = store.runActions([
      {
        id: 'm1',
        name: 'send_message',
        arguments: { conversationId: '101', content: 'hi' }
      }
    ])

    await Promise.resolve()
    store.cancelRun()
    await vi.runAllTimersAsync()
    await promise

    expect(store.run.phase).toBe('idle')
    expect(store.run.cursor.visible).toBe(false)
  })

  it('resetRun clears queue and completed list', () => {
    const store = useLinkMateAgentStore()
    store.run.completed.push({
      action: { id: '1', name: 'navigate', arguments: { nav: 'chat' } },
      result: { ok: true }
    })
    store.run.queue = [{ id: '2', name: 'open_calendar', arguments: {} }]
    store.resetRun()
    expect(store.run.completed).toHaveLength(0)
    expect(store.run.queue).toHaveLength(0)
    expect(store.run.phase).toBe('idle')
  })

  it('runActionCursorSimulation updates cursor position', async () => {
    vi.useRealTimers()
    const { mountSimDomHarness, teardownSimDomHarness } = await import(
      '../linkmateAgent/test/simDomHarness'
    )
    mountSimDomHarness({ navKey: 'calendar' })

    const store = useLinkMateAgentStore()
    const handled = await store.runActionCursorSimulation({
      id: 'nav1',
      name: 'navigate',
      arguments: { nav: 'chat' }
    })

    expect(handled).toBe(true)
    expect(store.run.cursor.x).toBeGreaterThan(0)
    teardownSimDomHarness()
    vi.useFakeTimers()
  })

  it('currentStepLabel reflects current action', () => {
    const store = useLinkMateAgentStore()
    store.run.currentAction = { id: '1', name: 'open_calendar', arguments: {} }
    expect(store.currentStepLabel).toBeTruthy()
  })

  it('updates cursor state via setters', () => {
    const store = useLinkMateAgentStore()
    store.setCursorPosition(120, 240)
    store.setCursorClicking(true)
    store.showAgentCursor()
    expect(store.run.cursor).toMatchObject({ x: 120, y: 240, clicking: true, visible: true })
    store.hideAgentCursor()
    expect(store.run.cursor.visible).toBe(false)
    expect(store.run.cursor.clicking).toBe(false)
    store.setThinkingText('思考中')
    expect(store.run.thinkingText).toBe('思考中')
  })

  it('isRunning is true while executing', async () => {
    const store = useLinkMateAgentStore()
    const promise = store.runActions([{ id: 'a1', name: 'navigate', arguments: { nav: 'chat' } }])
    expect(store.isRunning).toBe(true)
    await vi.runAllTimersAsync()
    await promise
    expect(store.isRunning).toBe(false)
  })

  it('previewToolCall ignores updates while executing', () => {
    const store = useLinkMateAgentStore()
    store.run.phase = 'executing'
    store.previewToolCall({ id: 'p2', name: 'open_calendar', arguments: '{}' })
    expect(store.run.plannedActions).toHaveLength(0)
  })

  it('clearPlanning only resets when in planning phase', () => {
    const store = useLinkMateAgentStore()
    store.beginPlanning()
    store.previewToolCall({ id: 'p1', name: 'navigate', arguments: '{"nav":"chat"}' })
    store.clearPlanning()
    expect(store.run.phase).toBe('idle')
    expect(store.run.plannedActions).toHaveLength(0)

    store.run.phase = 'executing'
    store.run.plannedActions = []
    store.clearPlanning()
    expect(store.run.phase).toBe('executing')
  })

  it('previewToolCall from idle enters planning phase', () => {
    const store = useLinkMateAgentStore()
    store.previewToolCall({ id: 'p1', name: 'navigate', arguments: '{"nav":"chat"}' })
    expect(store.run.phase).toBe('planning')
    expect(store.run.plannedActions).toHaveLength(1)
  })

  it('handles localStorage errors when loading and persisting agent mode', () => {
    const getItem = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('denied')
    })
    setActivePinia(createPinia())
    expect(useLinkMateAgentStore().agentMode).toBe(false)
    getItem.mockRestore()

    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('storage full')
    })
    const store = useLinkMateAgentStore()
    expect(() => store.setAgentMode(true)).not.toThrow()
    setItem.mockRestore()
  })

  it('runActions clears simulated input after ui-handled send_message', async () => {
    const clearSpy = vi.spyOn(uiBridge, 'clearSimulatedInput')
    const store = useLinkMateAgentStore()
    vi.spyOn(store, 'waitForConfirm').mockResolvedValue(true)

    const promise = store.runActions([
      {
        id: 'm1',
        name: 'send_message',
        arguments: { conversationId: '101', content: '你好' }
      }
    ])
    await vi.runAllTimersAsync()
    await promise

    expect(clearSpy).toHaveBeenCalled()
  })
})
