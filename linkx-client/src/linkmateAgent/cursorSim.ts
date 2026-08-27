/**
 * 作者：yangleduo
 */
import { t } from '../i18n'
import type { LinkMateAgentAction } from './types'

export interface LinkMateCursorPoint {
  x: number
  y: number
}

export interface LinkMateCursorStep {
  point: LinkMateCursorPoint
  click?: boolean
  pauseMs?: number
}

/** 模拟真人操作的节奏参数 */
export const LM_CURSOR_TIMING = {
  /** 开始移动前的思考停顿 */
  thinkingBeforeMoveMs: 680,
  /** 鼠标按下时长 */
  clickDownMs: 220,
  /** 松开后的短暂停顿 */
  clickUpSettleMs: 140,
  /** 每步默认停留 */
  pauseAfterStepMs: 520,
  /** 短距离移动耗时 */
  moveMinMs: 820,
  /** 长距离移动耗时 */
  moveMaxMs: 1650,
  /** 达到最大移动耗时的参考距离（px） */
  moveDistancePx: 920
} as const

function moveDurationForDistance(from: LinkMateCursorPoint, to: LinkMateCursorPoint): number {
  const dist = Math.hypot(to.x - from.x, to.y - from.y)
  const { moveMinMs, moveMaxMs, moveDistancePx } = LM_CURSOR_TIMING
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

function findNavButton(nav: string): Element | null {
  return document.querySelector(`[data-lm-nav="${nav}"]`)
}

function findSessionItem(conversationId: string, name: string): Element | null {
  if (conversationId) {
    const byId = document.querySelector(`[data-lm-session-id="${conversationId}"]`)
    if (byId) return byId
  }
  if (name) {
    const items = document.querySelectorAll('[data-lm-session-id]')
    const lower = name.toLowerCase()
    for (const item of items) {
      const label = item.getAttribute('data-lm-session-name') || item.textContent || ''
      if (label.includes(name) || label.toLowerCase().includes(lower)) {
        return item
      }
    }
  }
  return document.querySelector('[data-lm-session-id].active') ?? document.querySelector('[data-lm-session-id]')
}

function findChatInput(): Element | null {
  return (
    document.querySelector('[data-lm-chat-input]') ??
    document.querySelector('.message-input textarea') ??
    document.querySelector('.message-input')
  )
}

function findSendButton(): Element | null {
  return (
    document.querySelector('[data-lm-send-btn]') ??
    document.querySelector('.toolbar-right .lx-btn--send')
  )
}

function findSearchBar(): Element | null {
  return (
    document.querySelector('[data-lm-search-bar]') ??
    document.querySelector('.panel-search-bar .search-input') ??
    document.querySelector('.panel-search-bar')
  )
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
  const { pauseAfterStepMs } = LM_CURSOR_TIMING
  const push = (el: Element | null, click = true, pauseMs = pauseAfterStepMs) => {
    steps.push({ point: pointFromElement(el), click, pauseMs })
  }

  if (action.name === 'navigate') {
    const nav = asString(action.arguments.nav)
    push(findNavButton(nav), true, 620)
    return steps
  }

  if (action.name === 'open_chat') {
    const conversationId = asString(action.arguments.conversationId)
    const name = asString(action.arguments.name)
    push(findSessionItem(conversationId, name), true, 680)
    return steps
  }

  if (action.name === 'open_search') {
    push(findSearchBar(), true, 580)
    return steps
  }

  if (action.name === 'open_calendar') {
    push(findNavButton('calendar'), true, 620)
    return steps
  }

  if (action.name === 'send_message') {
    const conversationId = asString(action.arguments.conversationId)
    const name = asString(action.arguments.name)
    const session = findSessionItem(conversationId, name)
    if (session) push(session, true, 640)
    push(findChatInput(), true, 820)
    push(findSendButton(), true, 560)
    return steps
  }

  if (action.name === 'create_calendar_event') {
    push(findNavButton('calendar'), true, 620)
    return steps
  }

  if (action.name === 'add_favorite') {
    push(findNavButton('favorites'), true, 620)
    return steps
  }

  steps.push({ point: viewportFallback(), click: false, pauseMs: 420 })
  return steps
}

export async function animateCursorPath(
  steps: LinkMateCursorStep[],
  handlers: {
    getPosition: () => LinkMateCursorPoint
    setPosition: (point: LinkMateCursorPoint) => void
    setClicking: (clicking: boolean) => void
    isCancelled: () => boolean
  }
): Promise<void> {
  if (!steps.length) return

  const { thinkingBeforeMoveMs, clickDownMs, clickUpSettleMs, pauseAfterStepMs } = LM_CURSOR_TIMING
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

function sleep(ms: number) {
  return new Promise<void>(resolve => setTimeout(resolve, ms))
}
