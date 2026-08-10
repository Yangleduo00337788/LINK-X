/**
 * 作者：yangleduo
 */
import type { MessageNotification } from '../stores/notifications'

export interface OfficialBodyPart {
  kind: 'text' | 'image'
  text?: string
  key?: string
}

export interface OfficialFeedField {
  label: string
  value: string
}

export interface OfficialNotifyViewModel {
  id: string
  notifId: string
  time: string
  fullTime: string
  title: string
  dateLabel: string
  body?: string
  fields: OfficialFeedField[]
  footerHint?: string
  images: OfficialBodyPart[]
  unread: boolean
  type: string
  rawContent: string
  rawLines: string[]
}

type Translate = (key: string, params?: Record<string, unknown>) => string

const EVIDENCE_KEY_RE = /^\d+\.\s*([\w./-]+\.(?:png|jpe?g|gif|webp|bmp))$/i

export function formatOfficialDividerTime(raw: string): string {
  if (!raw) return ''
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return raw
  const m = date.getMonth() + 1
  const d = date.getDate()
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return `${m}月${d}日 ${hh}:${mm}`
}

export function formatOfficialCardDateLabel(raw: string): string {
  if (!raw) return ''
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return ''
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${m}-${d}`
}

export function formatOfficialFullTime(raw: string): string {
  if (!raw) return ''
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return raw
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return `${y}/${m}/${d} ${hh}:${mm}`
}

export function officialStatusTitle(t: Translate, type?: string): string {
  switch (type) {
    case 'feedback_submitted':
      return t('chat.officialNotifyTitleSubmitted')
    case 'feedback_replied':
      return t('chat.officialNotifyTitleReplied')
    case 'feedback_closed':
      return t('chat.officialNotifyTitleClosed')
    case 'feedback_reopened':
      return t('chat.officialNotifyTitleReopened')
    case 'review_approved':
      return t('chat.officialNotifyTitleReportDone')
    case 'review_rejected':
      return t('chat.officialNotifyTitleReportRejected')
    case 'notice_published':
      return t('chat.officialStepNotice')
    default:
      return t('chat.officialStepProgress')
  }
}

export function officialTypeLabel(t: Translate, type?: string): string {
  switch (type) {
    case 'feedback_submitted':
      return t('chat.officialTicket')
    case 'feedback_replied':
    case 'feedback_closed':
    case 'feedback_reopened':
      return t('chat.officialTicket')
    case 'review_approved':
    case 'review_rejected':
      return t('chat.officialNoticeTicket')
    case 'notice_published':
      return t('chat.officialNoticeTicket')
    default:
      return t('chat.officialStepProgress')
  }
}

function parseKvLine(line: string): OfficialFeedField | null {
  const idx = line.search(/[:：]/)
  if (idx <= 0) return null
  const label = line.slice(0, idx).trim()
  const value = line.slice(idx + 1).trim()
  if (!label || !value) return null
  return { label, value }
}

export function parseOfficialBodyParts(content?: string): OfficialBodyPart[] {
  if (!content) return []
  const parts: OfficialBodyPart[] = []
  for (const raw of content.split(/\r?\n/)) {
    const line = raw.trim()
    if (!line || /^【.+】$/.test(line)) continue
    if (/^证据图片:\s*$/.test(line)) continue
    const m = line.match(EVIDENCE_KEY_RE)
    if (m) {
      parts.push({ kind: 'image', key: m[1] })
      continue
    }
    if (/^证据图片:\s*无$/.test(line)) continue
    parts.push({ kind: 'text', text: line })
  }
  return parts
}

export function buildOfficialNotifyViewModel(
  notif: MessageNotification,
  t: Translate
): OfficialNotifyViewModel {
  const rawContent = notif.content || ''
  const rawLines = rawContent
    .split(/\r?\n/)
    .map(s => s.trim())
    .filter(Boolean)
  const parts = parseOfficialBodyParts(rawContent)
  const textLines = parts.filter(p => p.kind === 'text').map(p => p.text!)
  const imageParts = parts.filter(p => p.kind === 'image')

  let title = officialStatusTitle(t, notif.type)
  let body: string | undefined
  const fields: OfficialFeedField[] = []

  for (const line of textLines) {
    const bracket = line.match(/^【(.+)】$/)
    if (bracket?.[1]) {
      title = bracket[1].trim()
      continue
    }
    const kv = parseKvLine(line)
    if (!kv) continue
    if (kv.label === '详情') {
      body = kv.value
      continue
    }
    fields.push(kv)
  }

  if (!body && notif.type === 'feedback_submitted') {
    body = t('chat.officialBodySubmitted')
  }

  return {
    id: notif.id,
    notifId: notif.id,
    time: notif.createTime,
    fullTime: formatOfficialFullTime(notif.createTime),
    title,
    dateLabel: formatOfficialCardDateLabel(notif.createTime),
    body,
    fields,
    footerHint: t('chat.officialFooterHint'),
    images: imageParts,
    unread: notif.readStatus === 0,
    type: notif.type,
    rawContent,
    rawLines
  }
}
