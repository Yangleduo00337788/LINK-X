import type { MessageNotification } from '../stores/notifications'
import {
  formatOfficialCardDateLabel,
  formatOfficialDividerTime
} from './officialNotifyContent'

export interface CalendarRemindViewModel {
  id: string
  notifId: string
  time: string
  title: string
  dateLabel: string
  body: string
  unread: boolean
  relatedId?: string
}

type Translate = (key: string, params?: Record<string, unknown>) => string

export { formatOfficialDividerTime }

export function formatRemindContent(content: string | undefined, t: Translate): string {
  if (!content) return ''
  const raw = content.replace(/^[「【\[]([^」】\]]*)[」】\]]\s*/, '$1 ').trim()
  const ahead = raw.match(/^(?:(.+?)\s+)?将于\s+(.+)$/)
  if (ahead?.[2]) {
    const title = (ahead[1] || '').trim()
    return title
      ? t('chat.remindAtWithTitle', { time: ahead[2], title })
      : t('chat.remindAt', { time: ahead[2] })
  }
  const started = raw.match(/^(.+?\s+\d{1,2}:\d{2})\s+已开始\s*·\s*(.+)$/)
  if (started) {
    return t('chat.remindStartedWithTitle', { time: started[1], title: started[2] })
  }
  return raw
}

function extractEventTitle(content: string): string {
  const parts = content.split(/\s*[·•]\s*/)
  if (parts.length >= 2) {
    return parts[parts.length - 1].trim()
  }
  const bracket = content.match(/^[「【\[]([^」】\]]+)[」】\]]/)
  if (bracket?.[1]) return bracket[1].trim()
  return ''
}

export function buildCalendarRemindViewModel(
  notif: MessageNotification,
  t: Translate,
  eventTitle?: string
): CalendarRemindViewModel {
  const content = notif.content || ''
  const parsedTitle = eventTitle?.trim() || extractEventTitle(content)
  return {
    id: notif.id,
    notifId: notif.id,
    time: notif.createTime,
    title: parsedTitle || t('chat.calendarRemind'),
    dateLabel: formatOfficialCardDateLabel(notif.createTime),
    body: formatRemindContent(content, t),
    unread: notif.readStatus === 0,
    relatedId: notif.relatedId
  }
}
