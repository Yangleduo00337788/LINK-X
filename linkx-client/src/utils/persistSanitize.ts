// 消息类型定义
import type { ChatMessage } from '../types'

// 持久化时单条图片 data URL 最大字符数，超出则替换为占位符
const MAX_PERSIST_IMAGE_CHARS = 120_000

/**
 * 持久化前清理消息中的大体积/临时 URL，防止 localStorage 超限。
 *
 * @param messagesBySession 按会话 ID 分组的消息 Map
 * @returns 清理后的副本，不修改原对象
 */
export function sanitizeMessagesForPersist(
  messagesBySession: Record<string, ChatMessage[]>
): Record<string, ChatMessage[]> {
  const out: Record<string, ChatMessage[]> = {}
  // 遍历每个会话的消息列表
  for (const [sessionId, messages] of Object.entries(messagesBySession)) {
    out[sessionId] = messages.map(msg => {
      const next = { ...msg } // 浅拷贝单条消息，避免改原 state
      // 过大的 base64 图片不持久化，改为文字占位
      if (
        (next.type === 'image' || next.isImage) &&
        next.content.startsWith('data:') &&
        next.content.length > MAX_PERSIST_IMAGE_CHARS
      ) {
        next.content = '[图片]'
        next.isImage = true
      }
      // blob: URL 刷新后失效，持久化前删除
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

/**
 * 持久化 app store 整包 state 前的入口 sanitize。
 *
 * @param state pinia-plugin-persistedstate 即将写入 localStorage 的状态
 */
export function sanitizeAppPersistState(state: Record<string, unknown>): Record<string, unknown> {
  const next = { ...state } // 拷贝顶层 state
  // 若包含 messagesBySession，对其做消息级清理
  if (next.messagesBySession && typeof next.messagesBySession === 'object') {
    next.messagesBySession = sanitizeMessagesForPersist(
      next.messagesBySession as Record<string, ChatMessage[]>
    )
  }
  return next
}
