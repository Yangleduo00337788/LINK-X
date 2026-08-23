/**
 * 作者：yangleduo
 */
const ENC_PREFIX = 'lxenc:v1:'

/** 短视频描述/评论展示用明文；密文（历史缓存或异常响应）不直接露出。 */
export function readableShortVideoText(text?: string | null): string {
  if (!text) return ''
  const trimmed = text.trim()
  if (trimmed.startsWith(ENC_PREFIX)) return ''
  return text
}

export function isEncryptedShortVideoText(text?: string | null): boolean {
  return Boolean(text?.trim().startsWith(ENC_PREFIX))
}
