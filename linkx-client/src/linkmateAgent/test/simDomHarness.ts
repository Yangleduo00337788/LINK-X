/**
 * 作者：yangleduo
 */
import { createPinia, setActivePinia } from 'pinia'
import { useAppStore } from '../../stores/app'
import { STANDARD_SESSIONS } from './fixtures'
import {
  registerAgentCalendarModalBridge,
  registerAgentChatInputBridge,
  registerAgentComprehensiveSearchBridge,
  registerAgentNoteEditorBridge
} from '../uiBridge'

export type SimHarnessState = {
  nav: string
  activeSessionId: string | null
  sentMessages: string[]
  calendarEvents: Array<{ title: string; date: string; time?: string; endTime?: string }>
  savedNotes: Array<{ title: string; content: string }>
  search: { open: boolean; keyword: string; searched: boolean }
}

const state: SimHarnessState = {
  nav: 'calendar',
  activeSessionId: null,
  sentMessages: [],
  calendarEvents: [],
  savedNotes: [],
  search: { open: false, keyword: '', searched: false }
}

let calendarForm = { title: '', date: '', startTime: '', endTime: '' }
let noteContent = ''

function q<T extends Element>(selector: string): T | null {
  return document.querySelector(selector) as T | null
}

function setNav(nav: string) {
  state.nav = nav
  useAppStore().navKey = nav as ReturnType<typeof useAppStore>['navKey']
  document.querySelectorAll('[data-panel]').forEach(panel => {
    panel.classList.toggle('is-visible', panel.getAttribute('data-panel') === nav)
  })
}

function selectSession(sessionId: string) {
  state.activeSessionId = sessionId
  document.querySelectorAll('[data-lm-session-id]').forEach(item => {
    item.classList.toggle('active', item.getAttribute('data-lm-session-id') === sessionId)
  })
}

function sendMessage() {
  const input = q<HTMLTextAreaElement>('[data-lm-chat-input]')
  const text = input?.value.trim()
  if (!text) return
  state.sentMessages.push(text)
  if (input) input.value = ''
}

function openCalendarModal() {
  calendarForm = { title: '', date: '', startTime: '09:00', endTime: '10:00' }
  document.getElementById('calendar-modal')?.classList.add('is-open')
}

function saveCalendarEvent() {
  const title = q<HTMLInputElement>('[data-lm-calendar-event-title]')?.value.trim() || calendarForm.title
  const date = q<HTMLInputElement>('[data-lm-calendar-event-date]')?.value || calendarForm.date
  const time = q<HTMLInputElement>('[data-lm-calendar-event-start-time]')?.value || calendarForm.startTime
  const endTime = q<HTMLInputElement>('[data-lm-calendar-event-end-time]')?.value || calendarForm.endTime
  if (!title || !date) return
  state.calendarEvents.push({ title, date, time, endTime })
  document.getElementById('calendar-modal')?.classList.remove('is-open')
}

function openNotePanel() {
  noteContent = ''
  document.getElementById('note-panel')?.classList.add('is-open')
  const content = q<HTMLElement>('[data-lm-note-content]')
  if (content) content.textContent = ''
}

function saveNote() {
  const content = q<HTMLElement>('[data-lm-note-content]')?.textContent?.trim() || noteContent
  if (!content) return
  const title = content.split('\n')[0]?.slice(0, 40) || content.slice(0, 40)
  state.savedNotes.push({ title, content })
  document.getElementById('note-panel')?.classList.remove('is-open')
}

function openSearchModal() {
  state.search.open = true
  state.search.searched = false
  document.getElementById('search-modal')?.classList.add('is-open')
}

function runSearch() {
  const input = q<HTMLInputElement>('[data-lm-comprehensive-search-input]')
  const keyword = input?.value.trim() || state.search.keyword
  state.search.keyword = keyword
  state.search.searched = Boolean(keyword)
  state.search.open = true
}

function wireDom() {
  document.querySelectorAll('[data-lm-nav]').forEach(btn => {
    btn.addEventListener('click', () => setNav(btn.getAttribute('data-lm-nav') || 'chat'))
  })
  document.querySelectorAll('[data-lm-session-id]').forEach(item => {
    item.addEventListener('click', () =>
      selectSession(item.getAttribute('data-lm-session-id') || '')
    )
  })
  q('[data-lm-send-btn]')?.addEventListener('click', sendMessage)
  q('[data-lm-chat-add-btn]')?.addEventListener('click', e => {
    const menu = document.getElementById('add-dropdown')
    if (!menu) return
    const btn = e.currentTarget as HTMLElement
    const rect = btn.getBoundingClientRect()
    menu.style.left = `${rect.left}px`
    menu.style.top = `${rect.bottom + 4}px`
    menu.classList.add('is-open')
  })
  q('[data-lm-add-friend]')?.addEventListener('click', () => {
    document.getElementById('add-dropdown')?.classList.remove('is-open')
    openSearchModal()
  })
  q('[data-lm-open-linkmate]')?.addEventListener('click', () => {
    document.getElementById('add-dropdown')?.classList.remove('is-open')
    document.getElementById('linkmate-panel')?.classList.add('is-open')
  })
  q('[data-lm-calendar-add]')?.addEventListener('click', openCalendarModal)
  q('[data-lm-calendar-event-save]')?.addEventListener('click', saveCalendarEvent)
  q('[data-lm-favorites-add]')?.addEventListener('click', openNotePanel)
  q('[data-lm-note-save]')?.addEventListener('click', saveNote)
  q('[data-lm-comprehensive-search-submit]')?.addEventListener('click', runSearch)
}

function registerBridges() {
  registerAgentChatInputBridge({
    setInputText: text => {
      const input = q<HTMLTextAreaElement>('[data-lm-chat-input]')
      if (input) input.value = text
    },
    getInputText: () => q<HTMLTextAreaElement>('[data-lm-chat-input]')?.value || '',
    focusInput: () => q<HTMLTextAreaElement>('[data-lm-chat-input]')?.focus(),
    clearInput: () => {
      const input = q<HTMLTextAreaElement>('[data-lm-chat-input]')
      if (input) input.value = ''
    }
  })

  registerAgentCalendarModalBridge({
    isOpen: () => document.getElementById('calendar-modal')?.classList.contains('is-open') === true,
    focusTitle: () => q<HTMLInputElement>('[data-lm-calendar-event-title]')?.focus(),
    setTitle: title => {
      calendarForm.title = title
      const el = q<HTMLInputElement>('[data-lm-calendar-event-title]')
      if (el) el.value = title
    },
    setDate: dateKey => {
      calendarForm.date = dateKey
      const el = q<HTMLInputElement>('[data-lm-calendar-event-date]')
      if (el) el.value = dateKey
    },
    setStartTime: hm => {
      calendarForm.startTime = hm
      const el = q<HTMLInputElement>('[data-lm-calendar-event-start-time]')
      if (el) el.value = hm
    },
    setEndTime: hm => {
      calendarForm.endTime = hm
      const el = q<HTMLInputElement>('[data-lm-calendar-event-end-time]')
      if (el) el.value = hm
    },
    save: async () => {
      saveCalendarEvent()
      return true
    }
  })

  registerAgentNoteEditorBridge({
    isOpen: () => document.getElementById('note-panel')?.classList.contains('is-open') === true,
    focusContent: () => q<HTMLElement>('[data-lm-note-content]')?.focus(),
    setContent: text => {
      noteContent = text
      const el = q<HTMLElement>('[data-lm-note-content]')
      if (el) el.textContent = text
    },
    save: async () => {
      saveNote()
      return true
    }
  })

  registerAgentComprehensiveSearchBridge({
    isOpen: () => document.getElementById('search-modal')?.classList.contains('is-open') === true,
    focusKeyword: () => q<HTMLInputElement>('[data-lm-comprehensive-search-input]')?.focus(),
    setKeyword: text => {
      state.search.keyword = text
      const el = q<HTMLInputElement>('[data-lm-comprehensive-search-input]')
      if (el) el.value = text
    },
    search: async () => {
      runSearch()
      return true
    }
  })
}

export function teardownSimDomHarness() {
  registerAgentChatInputBridge(null)
  registerAgentCalendarModalBridge(null)
  registerAgentNoteEditorBridge(null)
  registerAgentComprehensiveSearchBridge(null)
  document.body.innerHTML = ''
  state.nav = 'calendar'
  state.activeSessionId = null
  state.sentMessages = []
  state.calendarEvents = []
  state.savedNotes = []
  state.search = { open: false, keyword: '', searched: false }
}

export function mountSimDomHarness(options?: { navKey?: string; withBridges?: boolean }) {
  teardownSimDomHarness()
  window.__LM_CURSOR_FAST = true

  setActivePinia(createPinia())
  const app = useAppStore()
  app.sessions = [...STANDARD_SESSIONS]
  app.navKey = (options?.navKey ?? 'calendar') as typeof app.navKey

  document.body.innerHTML = `
    <nav>
      <button data-lm-nav="chat" data-panel="chat">聊天</button>
      <button data-lm-nav="contacts">联系人</button>
      <button data-lm-nav="calendar">日历</button>
      <button data-lm-nav="favorites">收藏</button>
    </nav>
    <section id="panel-chat" class="panel" data-panel="chat">
      <div class="panel-search-bar" data-lm-search-bar>
        <input class="search-input" />
        <button type="button" data-lm-chat-add-btn>+</button>
      </div>
      <ul class="chat-list">
        <li data-lm-session-id="101" data-lm-session-name="张三">张三</li>
        <li data-lm-session-id="201" data-lm-session-name="项目群">项目群</li>
      </ul>
      <textarea data-lm-chat-input></textarea>
      <button data-lm-send-btn>发送</button>
    </section>
    <section id="panel-contacts" class="panel" data-panel="contacts">
      <p>联系人面板</p>
    </section>
    <section id="panel-calendar" class="panel is-visible" data-panel="calendar">
      <button data-lm-calendar-add>新建</button>
    </section>
    <section id="panel-favorites" class="panel" data-panel="favorites">
      <button data-lm-favorites-add>新建笔记</button>
    </section>
    <div id="add-dropdown" class="n-dropdown-menu">
      <div class="n-dropdown-option" data-lm-add-friend="1">添加好友</div>
      <div class="n-dropdown-option" data-lm-open-linkmate="1">打开灵伴</div>
    </div>
    <div id="linkmate-panel" class="modal"></div>
    <div id="calendar-modal" class="modal">
      <input data-lm-calendar-event-title />
      <input data-lm-calendar-event-date />
      <input data-lm-calendar-event-start-time type="time" value="09:00" />
      <input data-lm-calendar-event-end-time type="time" value="10:00" />
      <button data-lm-calendar-event-save>保存</button>
    </div>
    <div id="note-panel" class="modal">
      <main data-lm-note-content contenteditable="true"></main>
      <button data-lm-note-save>保存</button>
    </div>
    <div id="search-modal" class="modal">
      <input data-lm-comprehensive-search-input />
      <button data-lm-comprehensive-search-submit>搜索</button>
    </div>
  `

  setNav(options?.navKey ?? 'calendar')
  wireDom()
  if (options?.withBridges !== false) {
    registerBridges()
  }

  return {
    getState: () => ({
      nav: state.nav,
      activeSessionId: state.activeSessionId,
      sentMessages: [...state.sentMessages],
      calendarEvents: [...state.calendarEvents],
      savedNotes: [...state.savedNotes],
      search: { ...state.search }
    })
  }
}

export function createSimCursorHandlers(options?: { cancelled?: () => boolean }) {
  let pos = { x: 400, y: 300 }
  return {
    getPosition: () => ({ ...pos }),
    setPosition: (point: { x: number; y: number }) => {
      pos = { ...point }
    },
    setClicking: () => {},
    setThinking: () => {},
    isCancelled: options?.cancelled ?? (() => false)
  }
}
