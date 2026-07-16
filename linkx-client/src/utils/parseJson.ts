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

// Number.MAX_SAFE_INTEGER = 9007199254740991（16 位）
// 实际雪花 ID 远超此值，但为防止某些场景返回 15-16 位数字被截断，这里用更宽松的 13 位阈值
// 13 位毫秒时间戳仍然能精确表示，超过这个值的数字 ID 全部强制转字符串
const ID_STRINGIFY_THRESHOLD = 1e13

/**
 * 解析 JSON 时将大整数 ID 保留为字符串，避免雪花 ID 在 JS 中精度丢失。
 */
export function parseJsonPreservingIds<T = unknown>(raw: string): T {
  // 用阈值匹配所有"看起来像 ID"的大数字字段（>= 13 位），统一转为字符串
  const normalized = raw.replace(
    /"(id|fromUserId|toUserId|peerUserId|userId|friendId|requestId|conversationId|senderId)"\s*:\s*(\d{13,})/g,
    '"$1":"$2"'
  )
  return JSON.parse(normalized, (key, value) => {
    if (typeof value === 'number' && LONG_ID_FIELDS.has(key) && !Number.isSafeInteger(value)) {
      return String(value)
    }
    // 二次保险：尽管 reviver 已经处理了字符串值，但某些情况下（如嵌套对象中数字值）
    // 如果绝对值超过安全阈值且字段在 ID 列表中，也转字符串
    if (typeof value === 'number' && LONG_ID_FIELDS.has(key) && Math.abs(value) >= ID_STRINGIFY_THRESHOLD) {
      return String(value)
    }
    return value
  }) as T
}

/**
 * 简单 UUID v4 生成（不依赖 crypto.randomUUID 的环境兼容）。
 * 用于客户端生成 clientMsgId，确保去重 key 唯一性。
 */
export function generateUuidV4(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  // 回退实现
  const hex = (n: number) => n.toString(16).padStart(2, '0')
  const arr = new Uint8Array(16)
  if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
    crypto.getRandomValues(arr)
  } else {
    for (let i = 0; i < 16; i++) arr[i] = Math.floor(Math.random() * 256)
  }
  // 设置版本和变体
  arr[6] = (arr[6] & 0x0f) | 0x40
  arr[8] = (arr[8] & 0x3f) | 0x80
  const parts: string[] = []
  for (let i = 0; i < 16; i++) parts.push(hex(arr[i]))
  return (
    parts.slice(0, 4).join('') + '-' +
    parts.slice(4, 6).join('') + '-' +
    parts.slice(6, 8).join('') + '-' +
    parts.slice(8, 10).join('') + '-' +
    parts.slice(10, 16).join('')
  )
}
