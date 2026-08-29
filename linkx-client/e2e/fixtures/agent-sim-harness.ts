/**
 * 作者：yangleduo
 */
import { createPinia, setActivePinia } from 'pinia'
import { watch } from 'vue'
import { useAppStore } from '../../src/stores/app'
import { useLinkMateAgentStore } from '../../src/stores/linkmateAgent'
import { simulateActionCursor } from '../../src/linkmateAgent/cursorSim'
import { normalizeAgentAction } from '../../src/linkmateAgent/executor'
import type { LinkMateAgentAction, LinkMateAgentToolName } from '../../src/linkmateAgent/types'
import { STANDARD_SESSIONS } from '../../src/linkmateAgent/test/fixtures'
import {
  registerAgentCalendarModalBridge,
  registerAgentChatInputBridge,
  registerAgentComprehensiveSearchBridge,
  registerAgentNoteEditorBridge
} from '../../src/linkmateAgent/uiBridge'

type HarnessState = {
  nav: string
  activeSessionId: string | null
  sentMessages: string[]
  calendarEvents: Array<{ title: string; date: string; time?: string; endTime?: string }>
  savedNotes: Array<{ title: string; content: string }>
  search: { open: boolean; keyword: string; searched: boolean }
}

const state: HarnessState = {
  nav: 'calendar',
  activeSessionId: null,
  sentMessages: [],
  calendarEvents: [],
  savedNotes: [],
  search: { open: false, keyword: '', searched: false }
}

let calendarForm = {
  title: '',
  date: '',
  startTime: '',
  endTime: ''
}

let noteContent = ''

const els = {
  currentNav: () => document.querySelector('[data-testid="current-nav"]') as HTMLElement | null,
  stateJson: () => document.querySelector('[data-testid="state-json"]') as HTMLElement | null,
  cursor: () => document.getElementById('agent-cursor') as HTMLElement | null,
  chatInput: () => document.querySelector('[data-lm-chat-input]') as HTMLTextAreaElement | null,
  sentMessages: () => document.querySelector('[data-testid="sent-messages"]') as HTMLElement | null,
  calendarEvents: () => document.querySelector('[data-testid="calendar-events"]') as HTMLElement | null,
  savedNotes: () => document.querySelector('[data-testid="saved-notes"]') as HTMLElement | null,
  searchResult: () => document.querySelector('[data-testid="search-result"]') as HTMLElement | null,
  searchInput: () =>
    document.querySelector('[data-lm-comprehensive-search-input]') as HTMLInputElement | null,
  calendarTitle: () =>
    document.querySelector('[data-lm-calendar-event-title]') as HTMLInputElement | null,
  calendarDate: () =>
    document.querySelector('[data-lm-calendar-event-date]') as HTMLInputElement | null,
  calendarStart: () =>
    document.querySelector('[data-lm-calendar-event-start-time]') as HTMLInputElement | null,
  calendarEnd: () =>
    document.querySelector('[data-lm-calendar-event-end-time]') as HTMLInputElement | null,
  noteContent: () => document.querySelector('[data-lm-note-content]') as HTMLElement | null
}

function renderState() {
  els.currentNav()!.textContent = state.nav
  els.stateJson()!.textContent = JSON.stringify(state, null, 2)
  els.sentMessages()!.textContent = state.sentMessages.join(' | ')
  els.calendarEvents()!.textContent = state.calendarEvents
    .map(e => `${e.title}@${e.date} ${e.time || ''}-${e.endTime || ''}`)
    .join(' | ')
  els.savedNotes()!.textContent = state.savedNotes.map(n => `${n.title}:${n.content}`).join(' | ')
  els.searchResult()!.textContent = state.search.searched
    ? `searched:${state.search.keyword}`
    : ''
}

function setNav(nav: string) {
  state.nav = nav
  const app = useAppStore()
  app.navKey = nav as typeof app.navKey

  document.querySelectorAll('[data-panel]').forEach(panel => {
    panel.classList.toggle('is-visible', panel.getAttribute('data-panel') === nav)
  })
  document.querySelectorAll('[data-nav-btn]').forEach(btn => {
    btn.classList.toggle('is-active', btn.getAttribute('data-nav-btn') === nav)
  })
  renderState()
}

function selectSession(sessionId: string) {
  state.activeSessionId = sessionId
  document.querySelectorAll('[data-session-id]').forEach(item => {
    item.classList.toggle('active', item.getAttribute('data-session-id') === sessionId)
  })
  renderState()
}

function sendMessage() {
  const text = els.chatInput()?.value.trim()
  if (!text) return
  state.sentMessages.push(text)
  if (els.chatInput()) els.chatInput()!.value = ''
  renderState()
}

function openAddDropdown(button: HTMLElement) {
  const menu = document.getElementById('add-dropdown')
  if (!menu) return
  const rect = button.getBoundingClientRect()
  menu.style.left = `${rect.left}px`
  menu.style.top = `${rect.bottom + 4}px`
  menu.classList.add('is-open')
}

function closeAddDropdown() {
  document.getElementById('add-dropdown')?.classList.remove('is-open')
}

function openCalendarModal() {
  calendarForm = { title: '', date: '', startTime: '09:00', endTime: '10:00' }
  const modal = document.getElementById('calendar-modal')
  modal?.classList.add('is-open')
  if (els.calendarTitle()) els.calendarTitle()!.value = ''
  if (els.calendarDate()) els.calendarDate()!.value = ''
  if (els.calendarStart()) els.calendarStart()!.value = '09:00'
  if (els.calendarEnd()) els.calendarEnd()!.value = '10:00'
}

function saveCalendarEvent() {
  const title = els.calendarTitle()?.value.trim() || calendarForm.title
  const date = els.calendarDate()?.value || calendarForm.date
  const time = els.calendarStart()?.value || calendarForm.startTime
  const endTime = els.calendarEnd()?.value || calendarForm.endTime
  if (!title || !date) return
  state.calendarEvents.push({ title, date, time, endTime })
  document.getElementById('calendar-modal')?.classList.remove('is-open')
  renderState()
}

function openNotePanel() {
  noteContent = ''
  const panel = document.getElementById('note-panel')
  panel?.classList.add('is-open')
  if (els.noteContent()) els.noteContent()!.textContent = ''
}

function saveNote() {
  const content = els.noteContent()?.textContent?.trim() || noteContent
  if (!content) return
  const title = content.split('\n')[0]?.slice(0, 40) || content.slice(0, 40)
  state.savedNotes.push({ title, content })
  document.getElementById('note-panel')?.classList.remove('is-open')
  renderState()
}

function openSearchModal() {
  state.search.open = true
  state.search.searched = false
  document.getElementById('search-modal')?.classList.add('is-open')
  renderState()
}

function runSearch() {
  const keyword = els.searchInput()?.value.trim() || state.search.keyword
  state.search.keyword = keyword
  state.search.searched = Boolean(keyword)
  state.search.open = true
  renderState()
}

function wireDom() {
  document.querySelectorAll('[data-lm-nav]').forEach(btn => {
    btn.addEventListener('click', () => setNav(btn.getAttribute('data-lm-nav') || 'chat'))
  })

  document.querySelectorAll('[data-session-id]').forEach(item => {
    item.addEventListener('click', () => selectSession(item.getAttribute('data-session-id') || ''))
  })

  document.querySelector('[data-lm-send-btn]')?.addEventListener('click', sendMessage)

  document.querySelector('[data-lm-chat-add-btn]')?.addEventListener('click', e => {
    openAddDropdown(e.currentTarget as HTMLElement)
  })

  document.querySelector('[data-lm-add-friend]')?.addEventListener('click', () => {
    closeAddDropdown()
    openSearchModal()
  })

  document.querySelector('[data-lm-open-linkmate]')?.addEventListener('click', () => {
    closeAddDropdown()
    document.getElementById('linkmate-panel')?.classList.add('is-open')
  })

  document.querySelector('[data-lm-calendar-add]')?.addEventListener('click', openCalendarModal)
  document.querySelector('[data-lm-calendar-event-save]')?.addEventListener('click', saveCalendarEvent)
  document.querySelector('[data-lm-favorites-add]')?.addEventListener('click', openNotePanel)
  document.querySelector('[data-lm-note-save]')?.addEventListener('click', saveNote)
  document.querySelector('[data-lm-comprehensive-search-submit]')?.addEventListener('click', runSearch)
}

function registerBridges() {
  registerAgentChatInputBridge({
    setInputText: text => {
      const input = els.chatInput()
      if (input) input.value = text
    },
    getInputText: () => els.chatInput()?.value || '',
    focusInput: () => els.chatInput()?.focus(),
    clearInput: () => {
      const input = els.chatInput()
      if (input) input.value = ''
    }
  })

  registerAgentCalendarModalBridge({
    isOpen: () => document.getElementById('calendar-modal')?.classList.contains('is-open') === true,
    focusTitle: () => els.calendarTitle()?.focus(),
    setTitle: title => {
      calendarForm.title = title
      if (els.calendarTitle()) els.calendarTitle()!.value = title
    },
    setDate: dateKey => {
      calendarForm.date = dateKey
      if (els.calendarDate()) els.calendarDate()!.value = dateKey
    },
    setStartTime: hm => {
      calendarForm.startTime = hm
      if (els.calendarStart()) els.calendarStart()!.value = hm
    },
    setEndTime: hm => {
      calendarForm.endTime = hm
      if (els.calendarEnd()) els.calendarEnd()!.value = hm
    },
    save: async () => {
      saveCalendarEvent()
      return true
    }
  })

  registerAgentNoteEditorBridge({
    isOpen: () => document.getElementById('note-panel')?.classList.contains('is-open') === true,
    focusContent: () => els.noteContent()?.focus(),
    setContent: text => {
      noteContent = text
      if (els.noteContent()) els.noteContent()!.textContent = text
    },
    save: async () => {
      saveNote()
      return true
    }
  })

  registerAgentComprehensiveSearchBridge({
    isOpen: () => document.getElementById('search-modal')?.classList.contains('is-open') === true,
    focusKeyword: () => els.searchInput()?.focus(),
    setKeyword: text => {
      state.search.keyword = text
      if (els.searchInput()) els.searchInput()!.value = text
    },
    search: async () => {
      runSearch()
      return true
    }
  })
}

let cursor = { x: 480, y: 320, visible: false, clicking: false }

async function runAction(name: LinkMateAgentToolName, args: Record<string, unknown>) {
  const action = normalizeAgentAction({
    id: `e2e-${Date.now()}`,
    name,
    arguments: args
  } as LinkMateAgentAction)

  const result = await simulateActionCursor(action, {
    getPosition: () => ({ x: cursor.x, y: cursor.y }),
    setPosition: point => {
      cursor.x = point.x
      cursor.y = point.y
      const el = els.cursor()
      if (!el) return
      el.classList.add('is-visible')
      el.style.left = `${point.x}px`
      el.style.top = `${point.y}px`
    },
    setClicking: clicking => {
      cursor.clicking = clicking
      els.cursor()?.classList.toggle('is-clicking', clicking)
    },
    isCancelled: () => false
  })

  renderState()
  return { uiHandled: result.uiHandled, state: { ...state } }
}

function syncAgentBar() {
  const store = useLinkMateAgentStore()
  const bar = document.getElementById('agent-bar')
  const phaseEl = document.querySelector('[data-testid="agent-phase"]') as HTMLElement | null
  const stepEl = document.querySelector('[data-testid="agent-step"]') as HTMLElement | null
  const phase = store.run.phase
  if (!bar || !phaseEl) return
  const active = phase !== 'idle'
  bar.hidden = !active
  phaseEl.textContent = phase
  if (stepEl) stepEl.textContent = store.currentStepLabel || ''
}

async function runPipeline(
  actions: Array<{ name: LinkMateAgentToolName; arguments: Record<string, unknown> }>,
  options?: { manualConfirm?: boolean; autoReject?: boolean }
) {
  const store = useLinkMateAgentStore()
  store.setAgentMode(true)

  const parsed = actions.map((item, index) =>
    normalizeAgentAction({
      id: `pipe-${index}`,
      name: item.name,
      arguments: item.arguments
    } as LinkMateAgentAction)
  )

  let confirmPoller: ReturnType<typeof setInterval> | null = null
  if (!options?.manualConfirm) {
    confirmPoller = setInterval(() => {
      if (store.run.phase === 'confirming') {
        if (options?.autoReject) store.rejectPendingConfirm()
        else store.approvePendingConfirm()
      }
      syncAgentBar()
    }, 40)
  }

  try {
    await store.runActions(parsed)
  } finally {
    if (confirmPoller) clearInterval(confirmPoller)
    syncAgentBar()
  }

  return {
    phase: store.run.phase,
    completed: store.run.completed.map(item => ({
      name: item.action.name,
      ok: item.result.ok,
      message: item.result.message ?? ''
    }))
  }
}

function wireAgentBar() {
  const store = useLinkMateAgentStore()
  document.querySelector('[data-testid="agent-confirm"]')?.addEventListener('click', () => {
    store.approvePendingConfirm()
    syncAgentBar()
  })
  document.querySelector('[data-testid="agent-reject"]')?.addEventListener('click', () => {
    store.rejectPendingConfirm()
    syncAgentBar()
  })
  document.querySelector('[data-testid="agent-cancel"]')?.addEventListener('click', () => {
    store.cancelRun()
    syncAgentBar()
  })

  watch(
    () => [store.run.phase, store.run.currentAction?.id, store.run.thinkingText] as const,
    () => syncAgentBar(),
    { immediate: true }
  )
}

function bootstrap() {
  window.__LM_CURSOR_FAST = true
  setActivePinia(createPinia())

  const app = useAppStore()
  app.sessions = [...STANDARD_SESSIONS]
  app.navKey = 'calendar'

  setNav('calendar')
  wireDom()
  registerBridges()
  wireAgentBar()
  renderState()

  window.__agentE2E = {
    ready: true,
    getState: () => ({ ...state }),
    setNav,
    runAction,
    runPipeline,
    getAgentPhase: () => useLinkMateAgentStore().run.phase
  }
}

bootstrap()

export type AgentE2EApi = {
  ready: boolean
  getState: () => HarnessState
  setNav: (nav: string) => void
  runAction: (
    name: LinkMateAgentToolName,
    args: Record<string, unknown>
  ) => Promise<{ uiHandled: boolean; state: HarnessState }>
  runPipeline: (
    actions: Array<{ name: LinkMateAgentToolName; arguments: Record<string, unknown> }>,
    options?: { manualConfirm?: boolean; autoReject?: boolean }
  ) => Promise<{
    phase: string
    completed: Array<{ name: string; ok: boolean; message: string }>
  }>
  getAgentPhase: () => string
}

declare global {
  interface Window {
    __LM_CURSOR_FAST?: boolean
    __agentE2E?: AgentE2EApi
  }
}
