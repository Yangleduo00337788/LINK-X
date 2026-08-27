/**
 * 作者：yangleduo
 */
import type { ChatSession, FavoriteItem } from '../types'
import { useAppStore } from '../stores/app'
import { useCalendarStore } from '../stores/calendar'
import { useChatModalsStore } from '../stores/chatModals'
import { useFavoritesStore } from '../stores/favorites'
import { t } from '../i18n'
import { getActionDefinition } from './actions'
import type {
  LinkMateActionResult,
  LinkMateAgentAction,
  LinkMateAgentToolName
} from './types'
import { isNavKey } from './types'

function asString(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

function truncate(text: string, max = 40): string {
  if (text.length <= max) return text
  return `${text.slice(0, max)}…`
}

function resolveChatSession(args: Record<string, unknown>): ChatSession | null {
  const app = useAppStore()
  const conversationId = asString(args.conversationId)
  const name = asString(args.name)

  if (conversationId) {
    return app.sessions.find(s => s.id === conversationId) ?? null
  }

  if (name) {
    const lower = name.toLowerCase()
    return (
      app.sessions.find(s => s.name === name) ??
      app.sessions.find(s => s.name.toLowerCase().includes(lower)) ??
      null
    )
  }

  return app.currentSession ?? null
}

function openChatSession(session: ChatSession) {
  const app = useAppStore()
  app.setNav('chat')
  app.selectSession(session)
}

async function executeNavigate(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  const navRaw = asString(args.nav)
  if (!isNavKey(navRaw)) {
    return { ok: false, message: t('linkmateAgent.invalidNav', { nav: navRaw || '?' }) }
  }
  const app = useAppStore()
  app.setNav(navRaw)
  return { ok: true, message: t('linkmateAgent.doneNavigate', { nav: t(`nav.${navRaw}`) }) }
}

async function executeOpenChat(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  const conversationId = asString(args.conversationId)
  const name = asString(args.name)
  if (!conversationId && !name) {
    return { ok: false, message: t('linkmateAgent.openChatMissingArgs') }
  }
  const session = resolveChatSession(args)
  if (!session) {
    if (name) {
      return { ok: false, message: t('linkmateAgent.chatNotFoundByName', { name }) }
    }
    return { ok: false, message: t('linkmateAgent.chatNotFound') }
  }
  openChatSession(session)
  return { ok: true, message: t('linkmateAgent.doneOpenChat', { name: session.name }) }
}

async function executeOpenSearch(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  const keyword = asString(args.keyword)
  const modals = useChatModalsStore()
  modals.openComprehensiveSearch(keyword || undefined)
  if (keyword) {
    return { ok: true, message: t('linkmateAgent.doneOpenSearch', { keyword }) }
  }
  return { ok: true, message: t('linkmateAgent.doneOpenSearchEmpty') }
}

async function executeOpenCalendar(): Promise<LinkMateActionResult> {
  const app = useAppStore()
  app.setNav('calendar')
  return { ok: true, message: t('linkmateAgent.doneOpenCalendar') }
}

async function executeSendMessage(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  const content = asString(args.content)
  if (!content) {
    return { ok: false, message: t('linkmateAgent.sendMessageEmpty') }
  }

  const session = resolveChatSession(args)
  if (!session) {
    const name = asString(args.name)
    if (name) {
      return { ok: false, message: t('linkmateAgent.chatNotFoundByName', { name }) }
    }
    return { ok: false, message: t('linkmateAgent.sendMessageNoSession') }
  }
  if (session.blocked) {
    return { ok: false, message: t('linkmateAgent.sendMessageBlocked') }
  }

  openChatSession(session)
  const app = useAppStore()
  await app.sendMessage(content, { type: 'text' })
  return {
    ok: true,
    message: t('linkmateAgent.doneSendMessage', {
      name: session.name,
      preview: truncate(content)
    })
  }
}

async function executeCreateCalendarEvent(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  const title = asString(args.title)
  const date = asString(args.date)
  if (!title) {
    return { ok: false, message: t('linkmateAgent.createEventMissingTitle') }
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) {
    return { ok: false, message: t('linkmateAgent.createEventInvalidDate') }
  }

  const app = useAppStore()
  const calendar = useCalendarStore()
  app.setNav('calendar')

  const eventId = await calendar.addEvent({
    title,
    date,
    time: asString(args.time) || undefined,
    endTime: asString(args.endTime) || undefined,
    color: asString(args.color) || undefined
  })
  if (!eventId) {
    return { ok: false, message: t('linkmateAgent.createEventFailed') }
  }

  const [year, month, day] = date.split('-').map(Number)
  await calendar.setSelectedDate(new Date(year, month - 1, day).getTime())
  return {
    ok: true,
    message: t('linkmateAgent.doneCreateEvent', { title, date })
  }
}

function parseFavoriteType(raw: string): FavoriteItem['type'] {
  if (raw === 'link' || raw === 'image' || raw === 'file' || raw === 'message' || raw === 'note') {
    return raw
  }
  return 'note'
}

async function executeAddFavorite(args: Record<string, unknown>): Promise<LinkMateActionResult> {
  const title = asString(args.title)
  if (!title) {
    return { ok: false, message: t('linkmateAgent.addFavoriteMissingTitle') }
  }
  const content = asString(args.content) || title
  const type = parseFavoriteType(asString(args.type) || 'note')

  const app = useAppStore()
  const favorites = useFavoritesStore()
  app.setNav('favorites')

  const ok = await favorites.add({
    title,
    content,
    preview: content,
    type
  })
  if (!ok) {
    return { ok: false, message: t('linkmateAgent.addFavoriteFailed') }
  }
  return { ok: true, message: t('linkmateAgent.doneAddFavorite', { title }) }
}

const EXECUTORS: Record<
  LinkMateAgentToolName,
  (args: Record<string, unknown>) => Promise<LinkMateActionResult>
> = {
  navigate: executeNavigate,
  open_chat: executeOpenChat,
  open_search: executeOpenSearch,
  open_calendar: () => executeOpenCalendar(),
  send_message: executeSendMessage,
  create_calendar_event: executeCreateCalendarEvent,
  add_favorite: executeAddFavorite
}

export async function executeLinkMateAction(action: LinkMateAgentAction): Promise<LinkMateActionResult> {
  const executor = EXECUTORS[action.name]
  if (!executor) {
    return { ok: false, message: t('linkmateAgent.unknownAction', { name: action.name }) }
  }
  try {
    return await executor(action.arguments)
  } catch (err) {
    const msg = err instanceof Error ? err.message : t('linkmateAgent.executeFailed')
    return { ok: false, message: msg }
  }
}

export function describeLinkMateAction(action: LinkMateAgentAction): string {
  const def = getActionDefinition(action.name)
  const base = t(def.labelKey)
  if (action.name === 'navigate') {
    const nav = asString(action.arguments.nav)
    return nav ? `${base}: ${t(`nav.${nav}`)}` : base
  }
  if (action.name === 'open_chat') {
    const name = asString(action.arguments.name) || asString(action.arguments.conversationId)
    return name ? `${base}: ${name}` : base
  }
  if (action.name === 'open_search') {
    const keyword = asString(action.arguments.keyword)
    return keyword ? `${base}: ${keyword}` : base
  }
  if (action.name === 'send_message') {
    const target =
      asString(action.arguments.name) ||
      asString(action.arguments.conversationId) ||
      t('linkmateAgent.currentChat')
    const preview = truncate(asString(action.arguments.content))
    return preview ? `${base} → ${target}: ${preview}` : `${base} → ${target}`
  }
  if (action.name === 'create_calendar_event') {
    const title = asString(action.arguments.title)
    const date = asString(action.arguments.date)
    return title && date ? `${base}: ${title} (${date})` : title ? `${base}: ${title}` : base
  }
  if (action.name === 'add_favorite') {
    const title = asString(action.arguments.title)
    return title ? `${base}: ${title}` : base
  }
  return base
}

/** 动作间短暂停顿，便于用户感知自动操作 */
export function actionStepDelayMs(): number {
  return 980
}
