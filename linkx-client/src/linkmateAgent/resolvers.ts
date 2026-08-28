/**
 * 作者：yangleduo
 */
import type { ChatMessage, ContactItem, CreateGroupMember, FavoriteItem } from '../types'
import type { CalendarEvent } from '../stores/calendar'
import { useAppStore } from '../stores/app'
import { useCalendarStore } from '../stores/calendar'
import { useContactsStore } from '../stores/contacts'
import { useFavoritesStore } from '../stores/favorites'
import { useNotificationsStore } from '../stores/notifications'
import { asString } from './agentUtils'

export function resolveCalendarEvent(args: Record<string, unknown>): CalendarEvent | null {
  const calendar = useCalendarStore()
  const eventId = asString(args.eventId)
  if (eventId) {
    return calendar.events.find(item => item.id === eventId) ?? null
  }
  const title = asString(args.title)
  const date = asString(args.date)
  if (!title && !date) return null
  const matches = calendar.events.filter(item => {
    if (title && item.title !== title && !item.title.includes(title)) return false
    if (date && item.date !== date) return false
    return true
  })
  return matches[0] ?? null
}

export function resolveFavoriteItem(args: Record<string, unknown>): FavoriteItem | null {
  const favorites = useFavoritesStore()
  const id = asString(args.favoriteId)
  if (id) {
    return favorites.items.find(item => item.id === id) ?? null
  }
  const title = asString(args.title)
  if (!title) return null
  const lower = title.toLowerCase()
  return (
    favorites.items.find(item => item.title === title) ??
    favorites.items.find(item => item.title.toLowerCase().includes(lower)) ??
    null
  )
}

export function resolveReplyMessage(
  sessionId: string,
  messageId: string
): ChatMessage | null {
  const app = useAppStore()
  const messages = app.messagesBySession[sessionId] ?? []
  return messages.find(item => item.id === messageId) ?? null
}

export function buildMessageContentWithMentions(
  content: string,
  mentionNames: string[]
): string {
  const trimmed = content.trim()
  const prefixes = mentionNames
    .map(name => name.trim())
    .filter(Boolean)
    .map(name => `@${name}`)
  if (!prefixes.length) return trimmed
  const mentionText = `${prefixes.join(' ')} `
  if (!trimmed) return mentionText.trimEnd()
  if (prefixes.some(prefix => trimmed.includes(prefix))) return trimmed
  return `${mentionText}${trimmed}`
}

export function resolveContactMembers(names: string[]): CreateGroupMember[] {
  const contacts = useContactsStore().items
  const members: CreateGroupMember[] = []
  const seen = new Set<string>()
  for (const name of names) {
    const lower = name.toLowerCase()
    const contact =
      contacts.find(item => item.name === name) ??
      contacts.find(item => item.name.toLowerCase() === lower) ??
      contacts.find(item => item.name.includes(name))
    if (!contact) continue
    const id = contact.userId || contact.id
    if (!id || seen.has(id)) continue
    seen.add(id)
    members.push({
      id,
      name: contact.name,
      avatarText: contact.avatarText,
      avatarColor: contact.avatarColor,
      avatarUrl: contact.avatarUrl
    })
  }
  return members
}

export function resolveFriendRequestId(args: Record<string, unknown>): string | null {
  const requestId = asString(args.requestId)
  if (requestId) return requestId
  const fromName = asString(args.fromName)
  if (!fromName) return null
  const notifications = useNotificationsStore()
  const lower = fromName.toLowerCase()
  const match =
    notifications.friendNotifs.find(item => item.name === fromName) ??
    notifications.friendNotifs.find(item => item.name.toLowerCase().includes(lower))
  return match?.requestId ?? null
}

export function resolveGroupInvitationId(args: Record<string, unknown>): string | null {
  const invitationId = asString(args.invitationId)
  if (invitationId) return invitationId
  const groupName = asString(args.groupName)
  if (!groupName) return null
  const notifications = useNotificationsStore()
  const lower = groupName.toLowerCase()
  const match =
    notifications.groupNotifs.find(item => item.groupName === groupName) ??
    notifications.groupNotifs.find(item => item.groupName.toLowerCase().includes(lower))
  return match?.invitationId ?? null
}

export function findContactByName(name: string): ContactItem | null {
  const contacts = useContactsStore().items
  const lower = name.toLowerCase()
  return (
    contacts.find(item => item.name === name) ??
    contacts.find(item => item.name.toLowerCase() === lower) ??
    contacts.find(item => item.name.includes(name)) ??
    null
  )
}
