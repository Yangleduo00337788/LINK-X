// 消息 / 会话类型
import type { ChatMessage, ChatSession } from '../types'
import { isEphemeralMediaUrl, stripEphemeralMediaUrl } from './mediaUrl'

// 持久化时单条图片 data URL 最大字符数，超出则替换为占位符
const MAX_PERSIST_IMAGE_CHARS = 120_000

/**
 * 持久化前清理消息中的大体积/临时/预签名 URL，防止 localStorage 超限与过期裂图。
 *
 * @param messagesBySession 按会话 ID 分组的消息 Map
 * @returns 清理后的副本，不修改原对象
 */
export function sanitizeMessagesForPersist(
  messagesBySession: Record<string, ChatMessage[]>
): Record<string, ChatMessage[]> {
  const out: Record<string, ChatMessage[]> = {}
  for (const [sessionId, messages] of Object.entries(messagesBySession)) {
    out[sessionId] = messages.map(msg => sanitizeMessageForPersist(msg))
  }
  return out
}

function sanitizeMessageForPersist(msg: ChatMessage): ChatMessage {
  const next = { ...msg }

  // 过大的 base64 图片不持久化，改为文字占位
  if (
    (next.type === 'image' || next.isImage) &&
    next.content.startsWith('data:') &&
    next.content.length > MAX_PERSIST_IMAGE_CHARS
  ) {
    next.content = '[图片]'
    next.isImage = true
  }

  // 图片 content 若是预签名/本机 MinIO，落盘无意义，下次进会话会重新拉取
  if ((next.type === 'image' || next.isImage) && isEphemeralMediaUrl(next.content)) {
    next.content = '[图片]'
  }

  if (next.voiceUrl?.startsWith('blob:') || isEphemeralMediaUrl(next.voiceUrl)) {
    delete next.voiceUrl
  }
  if (next.fileUrl?.startsWith('blob:') || isEphemeralMediaUrl(next.fileUrl)) {
    delete next.fileUrl
  }
  if (isEphemeralMediaUrl(next.senderAvatar)) {
    delete next.senderAvatar
  }

  if (next.replyTo) {
    next.replyTo = sanitizeMessageForPersist(next.replyTo)
  }

  return next
}

function sanitizeSessionForPersist(session: ChatSession): ChatSession {
  const next: ChatSession = {
    ...session,
    avatarUrl: stripEphemeralMediaUrl(session.avatarUrl) || undefined
  }
  if (session.memberAvatars?.length) {
    next.memberAvatars = session.memberAvatars.map(m => ({
      ...m,
      imageUrl: stripEphemeralMediaUrl(m.imageUrl) || undefined
    }))
  }
  return next
}

/**
 * 持久化 app store 整包 state 前的入口 sanitize。
 * 去掉 MinIO 预签名头像等，登录后由接口重新签发。
 *
 * @param state pinia-plugin-persistedstate 即将写入 localStorage 的状态
 */
export function sanitizeAppPersistState(state: Record<string, unknown>): Record<string, unknown> {
  const next = { ...state }

  if (next.messagesBySession && typeof next.messagesBySession === 'object') {
    next.messagesBySession = sanitizeMessagesForPersist(
      next.messagesBySession as Record<string, ChatMessage[]>
    )
  }

  if (Array.isArray(next.sessions)) {
    next.sessions = (next.sessions as ChatSession[]).map(sanitizeSessionForPersist)
  }

  if (next.userProfile && typeof next.userProfile === 'object') {
    const profile = { ...(next.userProfile as Record<string, unknown>) }
    if (typeof profile.avatar === 'string') {
      profile.avatar = stripEphemeralMediaUrl(profile.avatar)
    }
    next.userProfile = profile
  }

  if (next.savedLogin && typeof next.savedLogin === 'object') {
    const saved = { ...(next.savedLogin as Record<string, unknown>) }
    if (typeof saved.avatar === 'string') {
      saved.avatar = stripEphemeralMediaUrl(saved.avatar)
    }
    next.savedLogin = saved
  }

  return next
}

/** 通讯录持久化：去掉好友预签名头像 */
export function sanitizeContactsPersistState(state: {
  items?: Array<{ avatarUrl?: string; [k: string]: unknown }>
}): typeof state {
  if (!Array.isArray(state.items)) return state
  return {
    ...state,
    items: state.items.map(item => ({
      ...item,
      avatarUrl: stripEphemeralMediaUrl(item.avatarUrl) || undefined
    }))
  }
}
