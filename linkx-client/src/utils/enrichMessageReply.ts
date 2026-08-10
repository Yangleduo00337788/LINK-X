/**
 * 作者：yangleduo
 */
import type { ChatMessage } from '../types'
import { chatMessagePreviewText } from './messagePreviewText'

/** 用同会话已加载消息补全引用快照（昵称、类型、摘要） */
export function enrichMessageReplyQuotes(messages: ChatMessage[]): void {
  const byId = new Map<string, ChatMessage>()
  for (const m of messages) {
    if (m.id) byId.set(m.id, m)
  }
  for (const m of messages) {
    const reply = m.replyTo
    if (!reply?.id) continue
    const quoted = byId.get(reply.id)
    if (!quoted) continue
    if (!reply.content?.trim()) {
      reply.content = chatMessagePreviewText(quoted)
    }
    if (!reply.senderName?.trim() && quoted.senderName?.trim()) {
      reply.senderName = quoted.senderName
    }
    if (!reply.senderId && quoted.senderId) {
      reply.senderId = quoted.senderId
    }
    if (!reply.type || reply.type === 'text') {
      reply.type = quoted.type
      reply.isImage = quoted.isImage
      reply.fileName = quoted.fileName
    }
    if (quoted.isSelf) reply.isSelf = true
  }
}

/** 服务端回包缺引用字段时，保留乐观消息或发送时的引用快照 */
export function preserveReplyTo(
  next: ChatMessage,
  prev?: ChatMessage | null,
  fallback?: ChatMessage | null
): void {
  if (next.replyTo) return
  const source = prev?.replyTo || fallback
  if (source) next.replyTo = { ...source }
}
