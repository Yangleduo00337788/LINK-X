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
 * 将时间戳格式化为相对时间描述（中文）。
 * - 1 分钟内: "刚刚"
 * - 1-59 分钟: "N 分钟前"
 * - 1-23 小时: "N 小时前"
 * - 1 天: "昨天 HH:mm"
 * - 2-7 天: "N 天前"
 * - 超过 7 天: "M/DD" 或 "YYYY/MM/DD"
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

  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < day) return `${Math.floor(diff / hour)} 小时前`

  const date = new Date(ms)
  const nowDate = new Date()

  if (diff < 2 * day) {
    return `昨天 ${formatChatTime(ms)}`
  }
  if (diff < 7 * day) {
    return `${Math.floor(diff / day)} 天前`
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
