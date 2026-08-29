/**
 * 作者：yangleduo
 */
import { t } from '../i18n'
import { getActionDefinition } from './actions'
import { asString, truncate } from './agentUtils'
import {
  inferDefaultEndTime,
  inferDefaultStartTime,
  resolveEventDate
} from './dateResolve'
import { resolveChatSession } from './sessionResolve'
import { TOOL_EXECUTORS } from './toolExecutors'
import type {
  LinkMateActionResult,
  LinkMateAgentAction,
  LinkMateAgentToolName
} from './types'

function sleep(ms: number) {
  return new Promise<void>(resolve => setTimeout(resolve, ms))
}

/** 模拟鼠标前仅做不影响界面的准备（不再提前切换页面） */
export async function prepareActionForSimulation(action: LinkMateAgentAction): Promise<void> {
  const args = action.arguments
  if (action.name === 'send_message' || action.name === 'open_chat') {
    const session = resolveChatSession(args)
    if (session) {
      revealSessionInList(session.id)
    }
    await sleep(120)
  }
}

function revealSessionInList(sessionId: string) {
  const el = document.querySelector(`[data-lm-session-id="${CSS.escape(sessionId)}"]`)
  el?.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' })
}

export function normalizeAgentAction(action: LinkMateAgentAction): LinkMateAgentAction {
  if (
    action.name === 'create_calendar_event' ||
    action.name === 'update_calendar_event'
  ) {
    const rawDate = asString(action.arguments.date)
    if (!rawDate) return action
    const resolved = resolveEventDate(rawDate)
    const nextArgs: Record<string, unknown> = {
      ...action.arguments,
      date: resolved ?? rawDate
    }
    if (action.name === 'create_calendar_event') {
      const time = asString(action.arguments.time) || inferDefaultStartTime(rawDate)
      const endTime = asString(action.arguments.endTime) || inferDefaultEndTime(time)
      nextArgs.time = time
      nextArgs.endTime = endTime
    }
    return { ...action, arguments: nextArgs }
  }
  return action
}

function successResultForUiHandled(action: LinkMateAgentAction): LinkMateActionResult {
  const args = action.arguments
  switch (action.name) {
    case 'navigate': {
      const nav = asString(args.nav)
      return { ok: true, message: t('linkmateAgent.doneNavigate', { nav: t(`nav.${nav}`) }) }
    }
    case 'open_linkmate':
      return { ok: true, message: t('linkmateAgent.doneOpenLinkmate') }
    case 'open_calendar':
      return { ok: true, message: t('linkmateAgent.doneOpenCalendar') }
    case 'open_contacts':
      return { ok: true, message: t('linkmateAgent.doneOpenContacts') }
    case 'open_chat': {
      const session = resolveChatSession(args)
      const name =
        session?.name || asString(args.name) || asString(args.conversationId)
      return { ok: true, message: t('linkmateAgent.doneOpenChat', { name }) }
    }
    case 'send_message': {
      const session = resolveChatSession(args)
      const content = asString(args.content)
      return {
        ok: true,
        message: t('linkmateAgent.doneSendMessage', {
          name: session?.name ?? t('linkmateAgent.currentChat'),
          preview: truncate(content)
        })
      }
    }
    case 'open_search': {
      const keyword = asString(args.keyword)
      return keyword
        ? { ok: true, message: t('linkmateAgent.doneOpenSearch', { keyword }) }
        : { ok: true, message: t('linkmateAgent.doneOpenSearchEmpty') }
    }
    case 'create_calendar_event': {
      const title = asString(args.title)
      const date = asString(args.date)
      return { ok: true, message: t('linkmateAgent.doneCreateEvent', { title, date }) }
    }
    case 'update_calendar_event': {
      const title = asString(args.title) || asString(args.eventId)
      return { ok: true, message: t('linkmateAgent.doneUpdateEvent', { title }) }
    }
    case 'delete_calendar_event': {
      const title = asString(args.title) || asString(args.eventId)
      return { ok: true, message: t('linkmateAgent.doneDeleteEvent', { title }) }
    }
    case 'add_favorite': {
      const title = asString(args.title)
      return { ok: true, message: t('linkmateAgent.doneAddFavorite', { title }) }
    }
    case 'update_favorite': {
      const title = asString(args.title)
      return { ok: true, message: t('linkmateAgent.doneUpdateFavorite', { title }) }
    }
    case 'delete_favorite': {
      const title = asString(args.title)
      return { ok: true, message: t('linkmateAgent.doneDeleteFavorite', { title }) }
    }
    case 'tag_favorite': {
      const title = asString(args.title)
      return { ok: true, message: t('linkmateAgent.doneTagFavorite', { title }) }
    }
    case 'create_folder': {
      const name = asString(args.name)
      return { ok: true, message: t('linkmateAgent.doneCreateFolder', { name }) }
    }
    case 'add_friend': {
      const username = asString(args.username)
      return { ok: true, message: t('linkmateAgent.doneAddFriend', { name: username }) }
    }
    case 'handle_friend_request':
      return {
        ok: true,
        message:
          asString(args.action) === 'reject'
            ? t('linkmateAgent.doneRejectFriendRequest')
            : t('linkmateAgent.doneAcceptFriendRequest')
      }
    case 'handle_group_invitation':
      return {
        ok: true,
        message:
          asString(args.action) === 'reject'
            ? t('linkmateAgent.doneRejectGroupInvitation')
            : t('linkmateAgent.doneAcceptGroupInvitation')
      }
    case 'publish_moment':
      return { ok: true, message: t('linkmateAgent.donePublishMoment') }
    case 'publish_short_video':
      return { ok: true, message: t('linkmateAgent.doneOpenShortVideoPublish') }
    case 'send_red_packet': {
      const amount = asString(args.amount) || '0'
      return { ok: true, message: t('linkmateAgent.doneSendRedPacket', { amount }) }
    }
    case 'start_call': {
      const session = resolveChatSession(args)
      const name = session?.name ?? t('linkmateAgent.currentChat')
      return asString(args.callType) === 'video'
        ? { ok: true, message: t('linkmateAgent.doneStartVideoCall', { name }) }
        : { ok: true, message: t('linkmateAgent.doneStartVoiceCall', { name }) }
    }
    case 'create_group': {
      const groupName = asString(args.groupName) || t('defaults.group')
      return { ok: true, message: t('linkmateAgent.doneCreateGroup', { name: groupName }) }
    }
    case 'add_group_members':
      return {
        ok: true,
        message: t('linkmateAgent.doneAddGroupMembers', {
          count: asStringArray(args.memberNames).length || 1
        })
      }
    case 'update_setting':
      return { ok: true, message: t('linkmateAgent.doneUpdateSetting', { key: asString(args.key) }) }
    case 'recharge_balance': {
      const amount = asString(args.amount) || '0'
      return { ok: true, message: t('linkmateAgent.doneRecharge', { amount }) }
    }
    default:
      return { ok: true, message: t('linkmateAgent.runAllDone', { count: 1 }) }
  }
}

function asStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return []
  return value.map(item => asString(item)).filter(Boolean)
}

export async function executeLinkMateAction(
  action: LinkMateAgentAction,
  options?: { uiHandled?: boolean }
): Promise<LinkMateActionResult> {
  if (options?.uiHandled) {
    return successResultForUiHandled(action)
  }

  const executor = TOOL_EXECUTORS[action.name as LinkMateAgentToolName]
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
  const args = action.arguments

  if (action.name === 'navigate') {
    const nav = asString(args.nav)
    return nav ? `${base}: ${t(`nav.${nav}`)}` : base
  }
  if (action.name === 'open_chat') {
    const name = asString(args.name) || asString(args.conversationId)
    return name ? `${base}: ${name}` : base
  }
  if (action.name === 'open_search') {
    const keyword = asString(args.keyword)
    return keyword ? `${base}: ${keyword}` : base
  }
  if (action.name === 'send_message') {
    const target =
      asString(args.name) ||
      asString(args.conversationId) ||
      t('linkmateAgent.currentChat')
    const preview = truncate(asString(args.content))
    return preview ? `${base} → ${target}: ${preview}` : `${base} → ${target}`
  }
  if (
    action.name === 'create_calendar_event' ||
    action.name === 'update_calendar_event' ||
    action.name === 'delete_calendar_event'
  ) {
    const title = asString(args.title) || asString(args.eventId)
    const date = asString(args.date)
    return title && date ? `${base}: ${title} (${date})` : title ? `${base}: ${title}` : base
  }
  if (
    action.name === 'add_favorite' ||
    action.name === 'update_favorite' ||
    action.name === 'delete_favorite' ||
    action.name === 'tag_favorite'
  ) {
    const title = asString(args.title) || asString(args.favoriteId)
    return title ? `${base}: ${title}` : base
  }
  if (action.name === 'add_friend') {
    const username = asString(args.username)
    return username ? `${base}: ${username}` : base
  }
  if (action.name === 'create_folder') {
    const name = asString(args.name)
    return name ? `${base}: ${name}` : base
  }
  if (action.name === 'create_group') {
    const groupName = asString(args.groupName)
    return groupName ? `${base}: ${groupName}` : base
  }
  if (action.name === 'send_red_packet' || action.name === 'recharge_balance') {
    const amount = asString(args.amount)
    return amount ? `${base}: ${amount}` : base
  }
  if (action.name === 'update_setting') {
    const key = asString(args.key)
    return key ? `${base}: ${key}` : base
  }
  if (action.name === 'publish_moment') {
    const content = truncate(asString(args.content), 24)
    return content ? `${base}: ${content}` : base
  }
  return base
}

/** 动作间短暂停顿，便于用户感知自动操作 */
export function actionStepDelayMs(): number {
  return 980
}

export function summarizeAgentRun(
  completed: Array<{ action: LinkMateAgentAction; result: LinkMateActionResult }>
): string {
  if (!completed.length) return ''
  const last = completed[completed.length - 1]
  if (last.result.message) return last.result.message
  const okCount = completed.filter(item => item.result.ok).length
  if (okCount === completed.length) {
    return t('linkmateAgent.runAllDone', { count: okCount })
  }
  return t('linkmateAgent.runPartialDone', {
    ok: okCount,
    total: completed.length
  })
}
