/**
 * 作者：yangleduo
 */
import { t } from '../i18n'
import type { ChatMessage } from '../types'

/** 将时间戳格式化为 HH:mm（兼容后端 Long 字符串） */
export function formatChatTime(timestamp?: string | number | null): string {
  const ms = typeof timestamp === 'string' ? Number(timestamp) : timestamp
  if (!ms || !Number.isFinite(ms)) return ''
  const date = new Date(ms)
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${hours}:${minutes}`
}

/**
 * 消息列表时间分割线文案（今天 HH:mm / 昨天 HH:mm / M月D日 HH:mm / YYYY年M月D日 HH:mm）
 */
export function formatMessageDivider(timestamp?: string | number | null): string {
  const ms = typeof timestamp === 'string' ? Number(timestamp) : timestamp
  if (!ms || !Number.isFinite(ms)) return ''
  const date = new Date(ms)
  const now = new Date()
  const hm = formatChatTime(ms)

  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const startOfThat = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
  const dayDiff = Math.round((startOfToday - startOfThat) / (24 * 60 * 60 * 1000))

  if (dayDiff === 0) return hm
  if (dayDiff === 1) return t('chat.timeYesterdayHm', { time: hm })
  if (date.getFullYear() === now.getFullYear()) {
    return t('chat.timeMonthDayHm', {
      month: date.getMonth() + 1,
      day: date.getDate(),
      time: hm
    })
  }
  return t('chat.timeYearMonthDayHm', {
    year: date.getFullYear(),
    month: date.getMonth() + 1,
    day: date.getDate(),
    time: hm
  })
}

/** 两条消息间隔超过此时长则插入时间分割线（毫秒） */
export const MESSAGE_TIME_GAP_MS = 5 * 60 * 1000

/** 为消息列表插入时间分割线（首条 + 间隔超过 5 分钟） */
export function buildMessagesWithTimeDividers(list: ChatMessage[]): ChatMessage[] {
  const result: ChatMessage[] = []
  let lastMs = 0
  for (const m of list) {
    const ms = m.createTime || 0
    if (m.type !== 'time' && ms && lastMs && ms - lastMs >= MESSAGE_TIME_GAP_MS) {
      result.push({
        id: `time-${m.id}`,
        sessionId: m.sessionId,
        content: formatMessageDivider(ms),
        time: m.time,
        createTime: ms,
        isSelf: false,
        type: 'time'
      })
    }
    result.push(m)
    if (ms) lastMs = ms
  }
  if (result.length > 0 && result[0].type !== 'time') {
    const first = result.find(m => m.type !== 'time')
    if (first?.createTime) {
      result.unshift({
        id: `time-start-${first.id}`,
        sessionId: first.sessionId,
        content: formatMessageDivider(first.createTime),
        time: first.time,
        createTime: first.createTime,
        isSelf: false,
        type: 'time'
      })
    }
  }
  return result
}

/**
 * 将时间戳格式化为相对时间描述。
 */
export function formatRelativeTime(timestamp?: string | number | null): string {
  const ms = typeof timestamp === 'string' ? Number(timestamp) : timestamp
  if (!ms || !Number.isFinite(ms)) return ''
  const now = Date.now()
  const diff = now - ms

  if (diff < 0) return formatChatTime(ms)

  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour

  if (diff < minute) return t('chat.justNow')
  if (diff < hour) return t('chat.minutesAgo', { n: Math.floor(diff / minute) })
  if (diff < day) return t('chat.hoursAgo', { n: Math.floor(diff / hour) })

  const date = new Date(ms)
  const nowDate = new Date()

  if (diff < 2 * day) {
    return t('chat.timeYesterdayHm', { time: formatChatTime(ms) })
  }
  if (diff < 7 * day) {
    return t('chat.daysAgo', { n: Math.floor(diff / day) })
  }

  const month = (date.getMonth() + 1).toString().padStart(2, '0')
  const dayStr = date.getDate().toString().padStart(2, '0')

  if (date.getFullYear() === nowDate.getFullYear()) {
    return `${month}/${dayStr}`
  }
  return `${date.getFullYear()}/${month}/${dayStr}`
}

/** 将字节数格式化为可读文件大小（兼容后端 Long 字符串） */
export function formatFileSize(bytes?: string | number | null): string {
  const size = typeof bytes === 'string' ? Number(bytes) : bytes
  if (!size || !Number.isFinite(size) || size < 0) return ''
  if (size === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let value = size
  let unitIndex = 0
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex += 1
  }
  return `${value >= 10 || unitIndex === 0 ? value.toFixed(0) : value.toFixed(2)} ${units[unitIndex]}`
}
