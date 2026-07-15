const LONG_ID_FIELDS = new Set([
  'id',
  'fromUserId',
  'toUserId',
  'peerUserId',
  'userId',
  'friendId',
  'requestId',
  'conversationId',
  'senderId'
])

/**
 * 解析 JSON 时将大整数 ID 保留为字符串，避免雪花 ID 在 JS 中精度丢失。
 */
export function parseJsonPreservingIds<T = unknown>(raw: string): T {
  const normalized = raw.replace(
    /"(id|fromUserId|toUserId|peerUserId|userId|friendId|requestId|conversationId|senderId)"\s*:\s*(\d{16,})/g,
    '"$1":"$2"'
  )
  return JSON.parse(normalized, (key, value) => {
    if (typeof value === 'number' && LONG_ID_FIELDS.has(key) && !Number.isSafeInteger(value)) {
      return String(value)
    }
    return value
  }) as T
}
