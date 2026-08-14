/**
 * 作者：yangleduo
 */
import { useAppStore } from '../stores/app'
import { SYSTEM_NOTIFY_SESSION_ID, OFFICIAL_NOTIFY_SESSION_ID } from '../types'
import type { ChatSession } from '../types'
import { chatMessagePreviewText } from './messagePreviewText'
import { t } from '../i18n'

export interface LinkMateImContext {
  conversationId: string
  title: string
  group: boolean
  messages: Array<{
    sender: string
    content: string
    time: string
    self: boolean
  }>
}

const MAX_CONTEXT_MESSAGES = 20

function isAttachableImSession(session: ChatSession | null | undefined): session is ChatSession {
  if (!session?.isReal) return false
  if (
    session.isSystemNotify ||
    session.isOfficialNotify ||
    session.id === SYSTEM_NOTIFY_SESSION_ID ||
    session.id === OFFICIAL_NOTIFY_SESSION_ID
  ) {
    return false
  }
  return true
}

/** 真实 IM 会话（群聊 / 单聊），不含系统通知类虚拟会话 */
export function isRealImChatSession(session: ChatSession | null | undefined): session is ChatSession {
  return isAttachableImSession(session)
}

/** 当前可接入灵伴的 IM 会话（用于 UI 展示，不要求已有消息） */
export function getActiveImSession(): Pick<LinkMateImContext, 'conversationId' | 'title' | 'group'> | undefined {
  const app = useAppStore()
  const session = app.currentSession
  if (!isAttachableImSession(session)) return undefined
  return {
    conversationId: session.id,
    title: session.name,
    group: !!session.isGroup
  }
}

/** 从当前 IM 会话构建灵伴上下文（发送时调用；消息可为空） */
export function buildImChatContext(): LinkMateImContext | undefined {
  const base = getActiveImSession()
  if (!base) return undefined

  const app = useAppStore()
  const session = app.currentSession!
  const recent = app.currentMessages
    .slice(-MAX_CONTEXT_MESSAGES)
    .map(msg => ({
      sender: msg.isSelf ? t('linkmate.you') : (msg.senderName || session.name),
      content: chatMessagePreviewText(msg),
      time: msg.time,
      self: !!msg.isSelf
    }))
    .filter(item => item.content.trim())

  return {
    ...base,
    messages: recent
  }
}
