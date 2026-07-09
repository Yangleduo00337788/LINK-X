import type { ChatMessage } from '../types'

const MAX_PERSIST_IMAGE_CHARS = 120_000

/** 持久化前剥离过大的 data URL，避免 localStorage 膨胀 */
export function sanitizeMessagesForPersist(
  messagesBySession: Record<string, ChatMessage[]>
): Record<string, ChatMessage[]> {
  const out: Record<string, ChatMessage[]> = {}
  for (const [sessionId, messages] of Object.entries(messagesBySession)) {
    out[sessionId] = messages.map(msg => {
      const next = { ...msg }
      if (
        (next.type === 'image' || next.isImage) &&
        next.content.startsWith('data:') &&
        next.content.length > MAX_PERSIST_IMAGE_CHARS
      ) {
        next.content = '[图片]'
        next.isImage = true
      }
      if (next.voiceUrl?.startsWith('blob:')) {
        delete next.voiceUrl
      }
      if (next.fileUrl?.startsWith('blob:')) {
        delete next.fileUrl
      }
      return next
    })
  }
  return out
}

export function sanitizeAppPersistState(state: Record<string, unknown>): Record<string, unknown> {
  const next = { ...state }
  if (next.messagesBySession && typeof next.messagesBySession === 'object') {
    next.messagesBySession = sanitizeMessagesForPersist(
      next.messagesBySession as Record<string, ChatMessage[]>
    )
  }
  return next
}
