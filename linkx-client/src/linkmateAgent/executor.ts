/**
 * 作者：yangleduo
 */
import { useAppStore } from '../stores/app'
import { useChatModalsStore } from '../stores/chatModals'
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
  const app = useAppStore()
  const conversationId = asString(args.conversationId)
  const name = asString(args.name)

  if (conversationId) {
    const session = app.sessions.find(s => s.id === conversationId)
    if (!session) {
      return { ok: false, message: t('linkmateAgent.chatNotFound') }
    }
    app.setNav('chat')
    app.selectSession(session)
    return { ok: true, message: t('linkmateAgent.doneOpenChat', { name: session.name }) }
  }

  if (name) {
    const lower = name.toLowerCase()
    const matched =
      app.sessions.find(s => s.name === name) ??
      app.sessions.find(s => s.name.toLowerCase().includes(lower))
    if (!matched) {
      return { ok: false, message: t('linkmateAgent.chatNotFoundByName', { name }) }
    }
    app.setNav('chat')
    app.selectSession(matched)
    return { ok: true, message: t('linkmateAgent.doneOpenChat', { name: matched.name }) }
  }

  return { ok: false, message: t('linkmateAgent.openChatMissingArgs') }
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

const EXECUTORS: Record<
  LinkMateAgentToolName,
  (args: Record<string, unknown>) => Promise<LinkMateActionResult>
> = {
  navigate: executeNavigate,
  open_chat: executeOpenChat,
  open_search: executeOpenSearch,
  open_calendar: () => executeOpenCalendar()
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
  return base
}

/** 动作间短暂停顿，便于用户感知自动操作 */
export function actionStepDelayMs(): number {
  return 420
}
