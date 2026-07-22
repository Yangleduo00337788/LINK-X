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
// 13 位毫秒时间戳仍然能精确表示，超过这个值的值全部强制转字符串
const ID_STRINGIFY_THRESHOLD = 1e13

/**
 * 检查是否是数组索引（数字形式的键）
 */
function isArrayIndex(key: string): boolean {
  return /^\d+$/.test(key)
}

/**
 * 解析 JSON 时将大整数 ID 保留为字符串，避免雪花 ID 在 JS 中精度丢失。
 * @param raw 原始 JSON 字符串
 * @returns 解析后的对象，或在解析失败时返回原始字符串
 */
export function parseJsonPreservingIds<T = unknown>(raw: string): T {
  // 先验证是否为有效 JSON（避免 JSON.parse 抛出异常）
  const trimmed = raw.trim()
  if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) {
    return raw as unknown as T
  }

  // 第一步：预处理所有 ID 相关字段的值，统一转为带引号的字符串形式
  // 匹配模式: "fieldName": 数字 或 "fieldName": 数字, 或 "fieldName": 数字}
  let normalized = raw.replace(
    /"(id|fromUserId|toUserId|peerUserId|userId|friendId|requestId|conversationId|senderId)"\s*:\s*(\d+)(?=[,\s\}])/g,
    '"$1":"$2"'
  )

  // 第二步：仅处理「数组元素」中的大数（避免把 createTime 等 13 位毫秒时间戳误转成字符串）
  // 将 [1234567890123456789, 9876543210987654321] 转为 ["1234567890123456789","9876543210987654321"]
  normalized = normalized.replace(/([\[,]\s*)(\d{13,})(?=[\s\],])/g, '$1"$2"')

  try {
    return JSON.parse(normalized, (key, value) => {
      // 如果是 LONG_ID_FIELDS 中的字段，无论值是否安全，都转为字符串
      if (LONG_ID_FIELDS.has(key)) {
        if (typeof value === 'string') return value
        if (typeof value === 'number') return String(value)
      }
      return value
    }) as T
  } catch {
    // 解析失败时返回原始字符串（保持原有行为）
    return raw as unknown as T
  }
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
