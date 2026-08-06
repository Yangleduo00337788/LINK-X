import type { ChatMessage } from '../types'

/** 雪花消息 ID 比较（同长度数字字符串） */
export function compareSnowflakeId(a: string, b: string): number {
  if (a === b) return 0
  if (!a || !b) return 0
  if (!/^\d+$/.test(a) || !/^\d+$/.test(b)) return 0
  if (a.length !== b.length) return a.length < b.length ? -1 : 1
  return a < b ? -1 : a > b ? 1 : 0
}

export function isMessageIdAtOrBefore(messageId: string, boundaryId: string): boolean {
  if (!messageId || !boundaryId) return false
  if (messageId === boundaryId) return true
  if (!/^\d+$/.test(messageId) || !/^\d+$/.test(boundaryId)) return false
  return compareSnowflakeId(messageId, boundaryId) <= 0
}

type Translate = (key: string, params?: Record<string, unknown>) => string

/** 文件卡片底部状态行（与 sendStatus 对齐） */
export function fileStatusFromSendStatus(
  msg: ChatMessage,
  t: Translate
): string {
  if (!msg.isSelf) return t('chat.fileStatusReceived')
  if (msg.sendStatus === 'failed') return t('chat.fileStatusFailed')
  if (
    msg.sendStatus === 'sending' &&
    msg.uploadProgress != null &&
    msg.uploadProgress < 100
  ) {
    return t('chat.fileStatusUploading', { n: msg.uploadProgress })
  }
  if (msg.sendStatus === 'sending') {
    return msg.fileStatus || t('chat.fileStatusSending')
  }
  if (msg.sendStatus === 'read') return t('chat.statusRead')
  if (msg.sendStatus === 'delivered') return t('chat.statusDelivered')
  if (msg.sendStatus === 'sent') return t('chat.statusSent')
  return msg.fileStatus || t('chat.fileStatusSent')
}

export function formatLastSeen(ts: number | undefined, t: Translate): string {
  if (!ts || !Number.isFinite(ts)) return ''
  const diff = Date.now() - ts
  if (diff < 60_000) return t('chat.lastSeenJustNow')
  if (diff < 3600_000) {
    const m = Math.max(1, Math.floor(diff / 60_000))
    return t('chat.lastSeenMinutes', { n: m })
  }
  if (diff < 86400_000) {
    const h = Math.max(1, Math.floor(diff / 3600_000))
    return t('chat.lastSeenHours', { n: h })
  }
  const d = Math.max(1, Math.floor(diff / 86400_000))
  if (d <= 7) return t('chat.lastSeenDays', { n: d })
  return t('chat.lastSeenLongAgo')
}

export function groupReadCountLabel(
  msg: ChatMessage,
  t: Translate
): string {
  if (!msg.isSelf || msg.totalMembers == null || msg.totalMembers <= 0) return ''
  const read = msg.readCount ?? 0
  return t('chat.readCount', { read, total: msg.totalMembers })
}

export function privateStatusLabel(msg: ChatMessage, t: Translate): string {
  if (!msg.isSelf) return ''
  if (msg.sendStatus === 'failed') {
    return msg.sendFailReason || t('chat.statusFailed')
  }
  if (
    msg.sendStatus === 'sending' &&
    msg.uploadProgress != null &&
    msg.uploadProgress < 100
  ) {
    return t('chat.statusUploading', { n: msg.uploadProgress })
  }
  if (msg.sendStatus === 'sending') return t('chat.statusSending')
  if (msg.sendStatus === 'read') return t('chat.statusRead')
  if (msg.sendStatus === 'delivered') return t('chat.statusDelivered')
  if (msg.sendStatus === 'sent') return t('chat.statusSent')
  return ''
}
