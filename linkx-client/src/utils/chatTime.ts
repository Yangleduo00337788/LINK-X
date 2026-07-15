/** 将时间戳格式化为 HH:mm（兼容后端 Long 字符串） */
export function formatChatTime(timestamp?: string | number | null): string {
  const ms = typeof timestamp === 'string' ? Number(timestamp) : timestamp
  if (!ms || !Number.isFinite(ms)) return ''
  const date = new Date(ms)
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${hours}:${minutes}`
}

/** 将字节数格式化为可读文件大小（兼容后端 Long 字符串） */
export function formatFileSize(bytes?: string | number | null): string {
  const size = typeof bytes === 'string' ? Number(bytes) : bytes
  if (!size || !Number.isFinite(size) || size <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let value = size
  let unitIndex = 0
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex += 1
  }
  return `${value >= 10 || unitIndex === 0 ? value.toFixed(0) : value.toFixed(1)} ${units[unitIndex]}`
}
