/**
 * 作者：yangleduo
 */
import { t } from '../i18n'
import { useAppStore } from '../stores/app'
import {
  inferDefaultEndTime,
  inferDefaultStartTime,
  resolveEventDate
} from './dateResolve'
import {
  getCalendarModalBridge,
  getComprehensiveSearchBridge,
  getNoteEditorBridge,
  getTypingThinkingLabel,
  simulateTyping,
  simulateTypingInto
} from './uiBridge'
import { resolveChatSessionId } from './sessionResolve'
import type { LinkMateAgentAction } from './types'

export interface LinkMateCursorPoint {
  x: number
  y: number
}

export interface LinkMateCursorStep {
  point: LinkMateCursorPoint
  click?: boolean
  pauseMs?: number
  /** 点击后逐字输入 */
  typeText?: string
  /** 真实点击目标（动画结束后触发） */
  target?: Element | null
  /** 点击后的等待/副作用 */
  afterClick?: () => void | Promise<void>
}

export interface LinkMateCursorSimulationResult {
  /** 已通过 UI 交互完成，无需 executor 重复执行 */
  uiHandled: boolean
}

/** 模拟真人操作的节奏参数 */
export const LM_CURSOR_TIMING = {
  thinkingBeforeMoveMs: 680,
  clickDownMs: 220,
  clickUpSettleMs: 140,
  pauseAfterStepMs: 520,
  moveMinMs: 820,
  moveMaxMs: 1650,
  moveDistancePx: 920,
  pageTransitionMs: 520,
  modalOpenMs: 460,
  fieldSettleMs: 380
} as const

declare global {
  interface Window {
    __LM_CURSOR_FAST?: boolean
  }
}

function isFastCursorMode(): boolean {
  return typeof window !== 'undefined' && window.__LM_CURSOR_FAST === true
}

function getCursorTiming() {
  if (!isFastCursorMode()) return LM_CURSOR_TIMING
  return {
    thinkingBeforeMoveMs: 8,
    clickDownMs: 8,
    clickUpSettleMs: 8,
    pauseAfterStepMs: 12,
    moveMinMs: 16,
    moveMaxMs: 24,
    moveDistancePx: LM_CURSOR_TIMING.moveDistancePx,
    pageTransitionMs: 12,
    modalOpenMs: 12,
    fieldSettleMs: 12
  }
}

function sleep(ms: number) {
  const delay = isFastCursorMode() ? Math.max(4, Math.round(ms * 0.05)) : ms
  return new Promise<void>(resolve => setTimeout(resolve, delay))
}

function moveDurationForDistance(from: LinkMateCursorPoint, to: LinkMateCursorPoint): number {
  const dist = Math.hypot(to.x - from.x, to.y - from.y)
  const { moveMinMs, moveMaxMs, moveDistancePx } = getCursorTiming()
  const ratio = Math.min(1, dist / moveDistancePx)
  return Math.round(moveMinMs + (moveMaxMs - moveMinMs) * ratio)
}

function asString(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

function getCenter(el: Element): LinkMateCursorPoint {
  const rect = el.getBoundingClientRect()
  return {
    x: rect.left + rect.width / 2,
    y: rect.top + rect.height / 2
  }
}

function revealElement(el: Element | null) {
  if (!el) return
  el.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' })
}

function viewportFallback(): LinkMateCursorPoint {
  return {
    x: window.innerWidth * 0.55,
    y: window.innerHeight * 0.45
  }
}

function pointFromElement(el: Element | null, fallback?: LinkMateCursorPoint): LinkMateCursorPoint {
  if (el) return getCenter(el)
  return fallback ?? viewportFallback()
}

function dispatchClick(el: Element | null | undefined) {
  if (!(el instanceof HTMLElement)) return
  el.dispatchEvent(
    new MouseEvent('mousedown', { bubbles: true, cancelable: true, view: window })
  )
  el.dispatchEvent(
    new MouseEvent('mouseup', { bubbles: true, cancelable: true, view: window })
  )
  el.click()
}

function findNavButton(nav: string): Element | null {
  const el = document.querySelector(`[data-lm-nav="${CSS.escape(nav)}"]`)
  revealElement(el)
  return el
}

function findSessionItem(conversationId: string, name: string, action?: LinkMateAgentAction): Element | null {
  const resolvedId =
    conversationId ||
    (action?.name === 'send_message' || action?.name === 'open_chat'
      ? resolveChatSessionId(action.arguments)
      : resolveChatSessionId({ conversationId, name }))
  if (resolvedId) {
    const byId = document.querySelector(`[data-lm-session-id="${CSS.escape(resolvedId)}"]`)
    if (byId) {
      revealElement(byId)
      return byId
    }
  }
  if (name) {
    const items = document.querySelectorAll('[data-lm-session-id]')
    const lower = name.toLowerCase()
    const exact: Element[] = []
    const partial: Element[] = []
    for (const item of items) {
      const label = item.getAttribute('data-lm-session-name') || ''
      const normalized = label.trim().toLowerCase()
      if (normalized === lower || label === name) exact.push(item)
      else if (label.includes(name) || normalized.includes(lower)) partial.push(item)
    }
    const pick = exact[0] ?? partial[0]
    if (pick) {
      revealElement(pick)
      return pick
    }
  }
  const fallback =
    document.querySelector('[data-lm-session-id].active') ?? document.querySelector('[data-lm-session-id]')
  revealElement(fallback)
  return fallback
}

function findChatInput(): Element | null {
  const el =
    document.querySelector('[data-lm-chat-input]') ??
    document.querySelector('.message-input textarea') ??
    document.querySelector('.message-input')
  revealElement(el)
  return el
}

function findSendButton(): Element | null {
  const el =
    document.querySelector('[data-lm-send-btn]') ??
    document.querySelector('.toolbar-right .lx-btn--send')
  revealElement(el)
  return el
}

function findSearchBar(): Element | null {
  const el =
    document.querySelector('[data-lm-search-bar] .search-input') ??
    document.querySelector('[data-lm-search-bar] input') ??
    document.querySelector('.panel-search-bar .search-input')
  revealElement(el)
  return el
}

function findChatAddButton(): Element | null {
  const el = document.querySelector('[data-lm-chat-add-btn]')
  revealElement(el)
  return el
}

function findComprehensiveSearchInput(): Element | null {
  const el = document.querySelector('[data-lm-comprehensive-search-input]')
  revealElement(el)
  return el
}

function findComprehensiveSearchSubmit(): Element | null {
  const el = document.querySelector('[data-lm-comprehensive-search-submit]')
  revealElement(el)
  return el
}

function findAddFriendDropdownOption(): Element | null {
  const options = document.querySelectorAll('.n-dropdown-option')
  for (const opt of options) {
    if (opt.getAttribute('data-lm-add-friend') === '1') return opt
    const text = (opt.textContent || '').trim()
    if (/好友|friend/i.test(text)) return opt
  }
  return null
}

function findLinkmateDropdownOption(): Element | null {
  const options = document.querySelectorAll('.n-dropdown-option')
  for (const opt of options) {
    if (opt.getAttribute('data-lm-open-linkmate') === '1') return opt
    const text = (opt.textContent || '').trim()
    if (/灵伴|linkmate/i.test(text)) return opt
  }
  return null
}

function findCalendarAddButton(): Element | null {
  const el = document.querySelector('[data-lm-calendar-add]')
  revealElement(el)
  return el
}

function findFavoritesAddButton(): Element | null {
  const el = document.querySelector('[data-lm-favorites-add]')
  revealElement(el)
  return el
}

function findCalendarTitleInput(): Element | null {
  const el = document.querySelector('[data-lm-calendar-event-title]')
  revealElement(el)
  return el
}

function findCalendarDateInput(): Element | null {
  const el = document.querySelector('[data-lm-calendar-event-date]')
  revealElement(el)
  return el
}

function findCalendarStartTimeInput(): Element | null {
  const el = document.querySelector('[data-lm-calendar-event-start-time]')
  revealElement(el)
  return el
}

function findCalendarEndTimeInput(): Element | null {
  const el = document.querySelector('[data-lm-calendar-event-end-time]')
  revealElement(el)
  return el
}

function findCalendarSaveButton(): Element | null {
  const el = document.querySelector('[data-lm-calendar-event-save]')
  revealElement(el)
  return el
}

function findNoteSaveButton(): Element | null {
  const el = document.querySelector('[data-lm-note-save]')
  revealElement(el)
  return el
}

function findNoteContentInput(): Element | null {
  const el = document.querySelector('[data-lm-note-content]')
  revealElement(el)
  return el
}

function clickStep(el: Element | null, pauseMs = getCursorTiming().pauseAfterStepMs): LinkMateCursorStep {
  return {
    point: pointFromElement(el),
    click: true,
    target: el,
    pauseMs
  }
}

function clickStepWithWait(
  el: Element | null,
  pauseMs = getCursorTiming().pauseAfterStepMs,
  waitMs = getCursorTiming().pageTransitionMs
): LinkMateCursorStep {
  return {
    ...clickStep(el, pauseMs),
    afterClick: async () => {
      dispatchClick(el)
      await sleep(waitMs)
    }
  }
}

function navStep(nav: string): LinkMateCursorStep {
  const el = findNavButton(nav)
  return {
    ...clickStep(el, 620),
    afterClick: async () => {
      dispatchClick(el)
      await sleep(getCursorTiming().pageTransitionMs)
    }
  }
}

function buildOpenLinkmateSteps(): LinkMateCursorStep[] {
  const steps: LinkMateCursorStep[] = []
  if (needsChatNav()) steps.push(navStep('chat'))
  const addBtn = findChatAddButton()
  steps.push({
    ...clickStep(addBtn, 360),
    afterClick: async () => {
      dispatchClick(addBtn)
      await sleep(320)
    }
  })
  const linkmateOption = findLinkmateDropdownOption()
  steps.push({
    ...clickStep(linkmateOption, getCursorTiming().pageTransitionMs),
    afterClick: async () => {
      dispatchClick(linkmateOption)
      await sleep(getCursorTiming().pageTransitionMs)
    }
  })
  return steps
}

function needsChatNav(): boolean {
  return useAppStore().navKey !== 'chat'
}

async function waitForSelector(selector: string, timeoutMs = 2400): Promise<Element | null> {
  const timeout = isFastCursorMode() ? 160 : timeoutMs
  const pollMs = isFastCursorMode() ? 16 : 80
  const start = Date.now()
  while (Date.now() - start < timeout) {
    const el = document.querySelector(selector)
    if (el) return el
    await sleep(pollMs)
  }
  return null
}

function findTimePickerOption(panel: Element, columnIndex: number, value: string): Element | null {
  const cols = panel.querySelectorAll('.n-time-picker-col')
  const col = cols.item(columnIndex)
  if (!col) return null
  const padded = value.padStart(2, '0')
  const items = col.querySelectorAll('.n-time-picker-col__item')
  for (const item of items) {
    const text = (item.textContent || '').trim()
    if (text === value || text === padded) return item
  }
  return null
}

async function simulateTimePicker(
  inputEl: Element | null,
  time: string,
  handlers: CursorHandlers,
  apply: (hm: string) => void
) {
  if (!inputEl || handlers.isCancelled()) return
  await animateCursorPath([clickStep(inputEl, 320)], handlers)
  await sleep(360)
  if (handlers.isCancelled()) return

  const panel = document.querySelector('.n-time-picker-panel')
  const [hh, mm] = time.split(':')
  if (panel && hh && mm) {
    const hourEl = findTimePickerOption(panel, 0, hh)
    if (hourEl) {
      await animateCursorPath([clickStep(hourEl, 260)], handlers)
      await sleep(240)
    }
    const minEl = findTimePickerOption(panel, 1, mm)
    if (minEl) {
      await animateCursorPath([clickStep(minEl, 260)], handlers)
      await sleep(240)
    }
    dispatchClick(inputEl)
    await sleep(180)
    return
  }

  apply(time)
  await sleep(getCursorTiming().fieldSettleMs)
}

export function getThinkingLabel(action: LinkMateAgentAction): string {
  if (action.name === 'navigate') {
    const nav = asString(action.arguments.nav)
    return nav
      ? t('linkmateAgent.thinkingNavigate', { nav: t(`nav.${nav}`) })
      : t('linkmateAgent.thinkingPlanning')
  }
  if (action.name === 'open_chat') {
    const name =
      asString(action.arguments.name) ||
      asString(action.arguments.conversationId) ||
      t('linkmateAgent.currentChat')
    return t('linkmateAgent.thinkingOpenChat', { name })
  }
  if (action.name === 'open_search') {
    const keyword = asString(action.arguments.keyword)
    return keyword
      ? t('linkmateAgent.thinkingOpenSearch', { keyword })
      : t('linkmateAgent.thinkingOpenSearchEmpty')
  }
  if (action.name === 'open_calendar') {
    return t('linkmateAgent.thinkingOpenCalendar')
  }
  if (action.name === 'open_contacts') {
    return t('linkmateAgent.thinkingOpenContacts')
  }
  if (action.name === 'open_linkmate') {
    return t('linkmateAgent.thinkingOpenLinkmate')
  }
  if (action.name === 'send_message') {
    return t('linkmateAgent.thinkingSendMessage')
  }
  if (action.name === 'create_calendar_event') {
    const title = asString(action.arguments.title)
    return title
      ? t('linkmateAgent.thinkingCreateEvent', { title })
      : t('linkmateAgent.thinkingCreateEventEmpty')
  }
  if (action.name === 'add_favorite') {
    const title = asString(action.arguments.title)
    return title
      ? t('linkmateAgent.thinkingAddFavorite', { title })
      : t('linkmateAgent.thinkingAddFavoriteEmpty')
  }
  return t('linkmateAgent.thinkingPlanning')
}

export function buildCursorSteps(action: LinkMateAgentAction): LinkMateCursorStep[] {
  const steps: LinkMateCursorStep[] = []
  const { pauseAfterStepMs } = getCursorTiming()
  const push = (el: Element | null, click = true, pauseMs = pauseAfterStepMs) => {
    steps.push({ point: pointFromElement(el), click, target: el, pauseMs })
  }

  if (action.name === 'navigate') {
    const nav = asString(action.arguments.nav)
    if (nav === 'linkmate') {
      return buildOpenLinkmateSteps()
    }
    steps.push(navStep(nav))
    return steps
  }

  if (action.name === 'open_linkmate') {
    return buildOpenLinkmateSteps()
  }

  if (action.name === 'open_chat') {
    if (needsChatNav()) steps.push(navStep('chat'))
    const conversationId = asString(action.arguments.conversationId)
    const name = asString(action.arguments.name)
    steps.push(
      clickStepWithWait(findSessionItem(conversationId, name, action), 680)
    )
    return steps
  }

  if (action.name === 'open_search') {
    if (needsChatNav()) steps.push(navStep('chat'))
    push(findChatAddButton(), true, 420)
    return steps
  }

  if (action.name === 'open_calendar') {
    steps.push(navStep('calendar'))
    return steps
  }

  if (action.name === 'open_contacts') {
    steps.push(navStep('contacts'))
    return steps
  }

  if (action.name === 'send_message') {
    if (needsChatNav()) steps.push(navStep('chat'))
    const conversationId = asString(action.arguments.conversationId)
    const name = asString(action.arguments.name)
    const content = asString(action.arguments.content)
    const session = findSessionItem(conversationId, name, action)
    if (session) steps.push(clickStepWithWait(session, 640))
    const input = findChatInput()
    steps.push({
      point: pointFromElement(input),
      click: true,
      target: input,
      pauseMs: 420,
      typeText: content
    })
    steps.push(clickStepWithWait(findSendButton(), 560, getCursorTiming().fieldSettleMs))
    return steps
  }

  if (action.name === 'create_calendar_event') {
    steps.push(navStep('calendar'))
    push(findCalendarAddButton(), true, 580)
    return steps
  }

  if (action.name === 'add_favorite') {
    steps.push(navStep('favorites'))
    push(findFavoritesAddButton(), true, 580)
    return steps
  }

  steps.push({ point: viewportFallback(), click: false, pauseMs: 420 })
  return steps
}

type CursorHandlers = {
  getPosition: () => LinkMateCursorPoint
  setPosition: (point: LinkMateCursorPoint) => void
  setClicking: (clicking: boolean) => void
  setThinking?: (text: string) => void
  isCancelled: () => boolean
}

export async function animateCursorPath(
  steps: LinkMateCursorStep[],
  handlers: CursorHandlers
): Promise<void> {
  if (!steps.length) return

  const { thinkingBeforeMoveMs, clickDownMs, clickUpSettleMs, pauseAfterStepMs } = getCursorTiming()
  await sleep(thinkingBeforeMoveMs)
  if (handlers.isCancelled()) return

  let current = handlers.getPosition()
  for (const step of steps) {
    if (handlers.isCancelled()) return

    const moveMs = moveDurationForDistance(current, step.point)
    await animateMove(current, step.point, handlers.setPosition, moveMs)
    current = step.point
    if (handlers.isCancelled()) return

    if (step.click) {
      await sleep(180)
      if (handlers.isCancelled()) return
      handlers.setClicking(true)
      await sleep(clickDownMs)
      handlers.setClicking(false)
      await sleep(clickUpSettleMs)
      if (step.afterClick) {
        await step.afterClick()
      } else {
        dispatchClick(step.target)
      }
    }

    if (step.typeText) {
      handlers.setThinking?.(getTypingThinkingLabel())
      await simulateTyping(step.typeText, {
        isCancelled: handlers.isCancelled
      })
      if (handlers.isCancelled()) return
      await sleep(320)
    }

    await sleep(step.pauseMs ?? pauseAfterStepMs)
  }
}

async function animateMove(
  from: LinkMateCursorPoint,
  to: LinkMateCursorPoint,
  setPosition: (point: LinkMateCursorPoint) => void,
  durationMs: number
): Promise<void> {
  const start = performance.now()
  await new Promise<void>(resolve => {
    function frame(now: number) {
      const t = Math.min(1, (now - start) / durationMs)
      const eased = t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2
      setPosition({
        x: from.x + (to.x - from.x) * eased,
        y: from.y + (to.y - from.y) * eased
      })
      if (t < 1) requestAnimationFrame(frame)
      else resolve()
    }
    requestAnimationFrame(frame)
  })
}

async function simulateCreateCalendarEvent(
  action: LinkMateAgentAction,
  handlers: CursorHandlers
): Promise<LinkMateCursorSimulationResult> {
  const title = asString(action.arguments.title)
  const rawDate = asString(action.arguments.date)
  const date = resolveEventDate(rawDate) ?? rawDate
  const time = asString(action.arguments.time) || inferDefaultStartTime(rawDate)
  const endTime = asString(action.arguments.endTime) || inferDefaultEndTime(time)
  const bridge = getCalendarModalBridge()

  await animateCursorPath([navStep('calendar')], handlers)
  if (handlers.isCancelled()) return { uiHandled: false }

  const addBtn = findCalendarAddButton()
  await animateCursorPath(
    [
      {
        ...clickStep(addBtn, 420),
        afterClick: async () => {
          dispatchClick(addBtn)
          await sleep(getCursorTiming().modalOpenMs)
        }
      }
    ],
    handlers
  )
  if (handlers.isCancelled()) return { uiHandled: false }

  await waitForSelector('[data-lm-calendar-event-title]')
  if (handlers.isCancelled()) return { uiHandled: false }

  const titleInput = findCalendarTitleInput()
  await animateCursorPath([clickStep(titleInput, 360)], handlers)
  if (handlers.isCancelled()) return { uiHandled: false }

  handlers.setThinking?.(getTypingThinkingLabel())
  if (bridge) {
    await simulateTypingInto(title, {
      isCancelled: handlers.isCancelled,
      setText: bridge.setTitle,
      focus: bridge.focusTitle
    })
  } else {
    await simulateTypingInto(title, {
      isCancelled: handlers.isCancelled,
      setText: text => {
        const input = titleInput as HTMLInputElement | null
        if (input) input.value = text
      },
      focus: () => (titleInput as HTMLElement | null)?.focus()
    })
  }
  if (handlers.isCancelled()) return { uiHandled: false }

  const dateInput = findCalendarDateInput()
  await animateCursorPath([clickStep(dateInput, 320)], handlers)
  if (handlers.isCancelled()) return { uiHandled: false }
  if (bridge) {
    bridge.setDate(date)
  } else {
    const el = dateInput as HTMLInputElement | null
    if (el) el.value = date
  }
  await sleep(getCursorTiming().fieldSettleMs)

  const startInput = findCalendarStartTimeInput()
  await simulateTimePicker(startInput, time, handlers, hm => {
    if (bridge) bridge.setStartTime(hm)
    else if (startInput) (startInput as HTMLInputElement).value = hm
  })
  if (handlers.isCancelled()) return { uiHandled: false }

  const endInput = findCalendarEndTimeInput()
  await simulateTimePicker(endInput, endTime, handlers, hm => {
    if (bridge) bridge.setEndTime(hm)
    else if (endInput) (endInput as HTMLInputElement).value = hm
  })
  if (handlers.isCancelled()) return { uiHandled: false }

  const saveBtn = findCalendarSaveButton()
  await animateCursorPath(
    [
      {
        ...clickStep(saveBtn, 520),
        afterClick: async () => {
          if (bridge) {
            await bridge.save()
          } else {
            dispatchClick(saveBtn)
          }
          await sleep(getCursorTiming().modalOpenMs)
        }
      }
    ],
    handlers
  )

  return { uiHandled: true }
}

async function simulateAddFavorite(
  action: LinkMateAgentAction,
  handlers: CursorHandlers
): Promise<LinkMateCursorSimulationResult> {
  const title = asString(action.arguments.title)
  const content = asString(action.arguments.content) || title
  const bridge = getNoteEditorBridge()

  await animateCursorPath([navStep('favorites')], handlers)
  if (handlers.isCancelled()) return { uiHandled: false }

  const addBtn = findFavoritesAddButton()
  await animateCursorPath(
    [
      {
        ...clickStep(addBtn, 420),
        afterClick: async () => {
          dispatchClick(addBtn)
          await sleep(getCursorTiming().modalOpenMs)
        }
      }
    ],
    handlers
  )
  if (handlers.isCancelled()) return { uiHandled: false }

  await waitForSelector('[data-lm-note-content], [data-lm-note-save]')
  if (handlers.isCancelled()) return { uiHandled: false }

  const contentInput = findNoteContentInput()
  await animateCursorPath([clickStep(contentInput, 360)], handlers)
  if (handlers.isCancelled()) return { uiHandled: false }

  handlers.setThinking?.(getTypingThinkingLabel())
  const text = content || title
  if (bridge) {
    await simulateTypingInto(text, {
      isCancelled: handlers.isCancelled,
      setText: bridge.setContent,
      focus: bridge.focusContent
    })
  } else {
    await simulateTypingInto(text, {
      isCancelled: handlers.isCancelled,
      setText: textVal => {
        const el = contentInput as HTMLElement | null
        if (el) el.textContent = textVal
      },
      focus: () => (contentInput as HTMLElement | null)?.focus()
    })
  }
  if (handlers.isCancelled()) return { uiHandled: false }

  const saveBtn = findNoteSaveButton()
  await animateCursorPath(
    [
      {
        ...clickStep(saveBtn, 520),
        afterClick: async () => {
          if (bridge) {
            await bridge.save()
          } else {
            dispatchClick(saveBtn)
          }
          await sleep(getCursorTiming().fieldSettleMs)
        }
      }
    ],
    handlers
  )

  return { uiHandled: true }
}

async function simulateOpenSearch(
  action: LinkMateAgentAction,
  handlers: CursorHandlers
): Promise<LinkMateCursorSimulationResult> {
  const keyword = asString(action.arguments.keyword)
  const bridge = getComprehensiveSearchBridge()

  if (needsChatNav()) {
    await animateCursorPath([navStep('chat')], handlers)
    if (handlers.isCancelled()) return { uiHandled: false }
  }

  const addBtn = findChatAddButton()
  await animateCursorPath(
    [
      {
        ...clickStep(addBtn, 360),
        afterClick: async () => {
          dispatchClick(addBtn)
          await sleep(320)
        }
      }
    ],
    handlers
  )
  if (handlers.isCancelled()) return { uiHandled: false }

  let friendOption: Element | null = null
  for (let i = 0; i < 8 && !friendOption; i++) {
    friendOption = findAddFriendDropdownOption()
    if (!friendOption) await sleep(80)
  }

  if (friendOption) {
    await animateCursorPath(
      [
        {
          ...clickStep(friendOption, 380),
          afterClick: async () => {
            dispatchClick(friendOption)
            await sleep(getCursorTiming().modalOpenMs)
          }
        }
      ],
      handlers
    )
  }
  if (handlers.isCancelled()) return { uiHandled: false }

  await waitForSelector('[data-lm-comprehensive-search-input]')
  if (handlers.isCancelled()) return { uiHandled: false }

  if (!keyword) {
    return { uiHandled: true }
  }

  const input = findComprehensiveSearchInput()
  await animateCursorPath([clickStep(input, 320)], handlers)
  if (handlers.isCancelled()) return { uiHandled: false }

  handlers.setThinking?.(getTypingThinkingLabel())
  if (bridge) {
    await simulateTypingInto(keyword, {
      isCancelled: handlers.isCancelled,
      setText: bridge.setKeyword,
      focus: bridge.focusKeyword
    })
  } else {
    await simulateTypingInto(keyword, {
      isCancelled: handlers.isCancelled,
      setText: text => {
        const el = input as HTMLInputElement | null
        if (el) el.value = text
      },
      focus: () => (input as HTMLInputElement | null)?.focus()
    })
  }
  if (handlers.isCancelled()) return { uiHandled: false }

  const submitBtn = findComprehensiveSearchSubmit()
  await animateCursorPath(
    [
      {
        ...clickStep(submitBtn, 520),
        afterClick: async () => {
          if (bridge) {
            await bridge.search()
          } else {
            dispatchClick(submitBtn)
          }
          await sleep(getCursorTiming().fieldSettleMs)
        }
      }
    ],
    handlers
  )

  return { uiHandled: true }
}

export async function simulateActionCursor(
  action: LinkMateAgentAction,
  handlers: CursorHandlers
): Promise<LinkMateCursorSimulationResult> {
  if (action.name === 'create_calendar_event') {
    return simulateCreateCalendarEvent(action, handlers)
  }
  if (action.name === 'add_favorite') {
    return simulateAddFavorite(action, handlers)
  }
  if (action.name === 'open_search') {
    return simulateOpenSearch(action, handlers)
  }
  if (action.name === 'open_linkmate' || (action.name === 'navigate' && asString(action.arguments.nav) === 'linkmate')) {
    const steps = buildOpenLinkmateSteps()
    await animateCursorPath(steps, handlers)
    return { uiHandled: true }
  }

  const steps = buildCursorSteps(action)
  await animateCursorPath(steps, handlers)

  return { uiHandled: true }
}
