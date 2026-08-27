/**
 * 作者：yangleduo
 */
import type { ChatSession } from '../types'
import { OFFICIAL_NOTIFY_SESSION_ID, SYSTEM_NOTIFY_SESSION_ID } from '../types'
import { useAppStore } from '../stores/app'
import { useContactsStore } from '../stores/contacts'

function asString(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

function normalizeName(value: string): string {
  return value.trim().toLowerCase()
}

function splitGroupTitle(name: string): string[] {
  return name.split(/[,，、\s]+/).map(part => part.trim()).filter(Boolean)
}

function isVirtualSession(session: ChatSession): boolean {
  return (
    !!session.isSystemNotify ||
    !!session.isOfficialNotify ||
    session.id === SYSTEM_NOTIFY_SESSION_ID ||
    session.id === OFFICIAL_NOTIFY_SESSION_ID
  )
}

function scoreSessionMatch(session: ChatSession, query: string): number {
  const q = normalizeName(query)
  if (!q) return 0

  const displayName = normalizeName(session.name)
  const groupName = normalizeName(session.groupName || '')
  const groupRemark = normalizeName(session.groupRemark || '')
  let score = 0

  if (session.name === query || displayName === q) score += 220
  if (!session.isGroup && displayName === q) score += 90
  if (session.isGroup && (displayName === q || groupName === q || groupRemark === q)) score += 70

  if (displayName.includes(q) || groupName.includes(q) || groupRemark.includes(q)) {
    score += 28
    if (session.isGroup) {
      const parts = splitGroupTitle(session.name)
      const partExact = parts.some(part => normalizeName(part) === q)
      if (partExact) score += 12
      else score -= 35
    }
  }

  if (!session.isGroup && query.length <= 10) score += 30
  if (session.isGroup && query.length <= 10) score -= 8

  if (isVirtualSession(session)) score -= 1000
  return score
}

function findSessionByContactName(name: string): ChatSession | null {
  const contacts = useContactsStore()
  const app = useAppStore()
  const q = normalizeName(name)

  const friend = contacts.friends.find(item => {
    const display = normalizeName(item.name)
    const nickname = normalizeName(item.nickname || '')
    const remark = normalizeName(item.remark || '')
    return (
      item.name === name ||
      display === q ||
      nickname === q ||
      remark === q ||
      (name.length >= 2 && (display.includes(q) || nickname.includes(q) || remark.includes(q)))
    )
  })
  if (!friend) return null

  const userId = friend.userId ? String(friend.userId) : friend.id
  const byPeer =
    app.sessions.find(session => !session.isGroup && session.peerUserId === userId) ??
    app.sessions.find(session => !session.isGroup && session.peerUserId === friend.id) ??
    app.sessions.find(
      session => !session.isGroup && normalizeName(session.name) === normalizeName(friend.name)
    )

  return byPeer ?? null
}

export function resolveChatSession(args: Record<string, unknown>): ChatSession | null {
  const app = useAppStore()
  const conversationId = asString(args.conversationId)
  const name = asString(args.name)
  const chatType = asString(args.chatType)

  if (conversationId) {
    return app.sessions.find(session => session.id === conversationId) ?? null
  }

  if (!name) {
    return app.currentSession ?? null
  }

  const preferGroup = chatType === 'group'
  const preferDirect = chatType === 'direct' || !preferGroup

  if (preferDirect) {
    const fromContacts = findSessionByContactName(name)
    if (fromContacts) return fromContacts
  }

  const ranked = app.sessions
    .filter(session => !isVirtualSession(session))
    .map(session => ({ session, score: scoreSessionMatch(session, name) }))
    .filter(item => item.score > 0)
    .sort((a, b) => b.score - a.score)

  if (!ranked.length) return null

  if (preferGroup) {
    const groupPick = ranked.find(item => item.session.isGroup)
    if (groupPick) return groupPick.session
  }

  if (preferDirect) {
    const directPick = ranked.find(item => !item.session.isGroup)
    if (directPick) {
      const top = ranked[0]
      if (!top.session.isGroup || directPick.score >= top.score - 25) {
        return directPick.session
      }
    }
  }

  return ranked[0].session
}

export function resolveChatSessionId(args: Record<string, unknown>): string {
  return resolveChatSession(args)?.id ?? ''
}
