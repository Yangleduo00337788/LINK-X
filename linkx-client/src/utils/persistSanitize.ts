/**
 * 作者：yangleduo
 */
// 消息 / 会话类型
import type { ChatMessage, ChatSession } from '../types'
import { resolveUserAvatarUrl } from './defaultAvatar'
import { isEphemeralMediaUrl, stripEphemeralMediaUrl } from './mediaUrl'
import { imagePreviewPlaceholder } from './messagePreviewText'

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

export function sanitizeMessageForPersist(msg: ChatMessage): ChatMessage {
  const next = { ...msg }

  // base64 图片一律不持久化（截图/小图避免明文落盘）；过大时同样占位
  if ((next.type === 'image' || next.isImage) && next.content.startsWith('data:')) {
    next.content = imagePreviewPlaceholder()
    next.isImage = true
  }

  // 图片 content 若是预签名/本机 MinIO，落盘无意义，下次进会话会重新拉取
  if ((next.type === 'image' || next.isImage) && isEphemeralMediaUrl(next.content)) {
    next.content = imagePreviewPlaceholder()
  }

  if (next.voiceUrl?.startsWith('blob:') || isEphemeralMediaUrl(next.voiceUrl)) {
    delete next.voiceUrl
  }
  if (next.fileUrl?.startsWith('blob:') || isEphemeralMediaUrl(next.fileUrl)) {
    delete next.fileUrl
  }
  if (next.senderId) {
    const stable = resolveUserAvatarUrl(next.senderAvatar, next.senderId)
    if (stable) next.senderAvatar = stable
    else if (isEphemeralMediaUrl(next.senderAvatar)) delete next.senderAvatar
  } else if (isEphemeralMediaUrl(next.senderAvatar)) {
    delete next.senderAvatar
  }

  if (next.replyTo) {
    next.replyTo = sanitizeMessageForPersist(next.replyTo)
  }

  return next
}

/** 从 peerUserId / userId 还原稳定头像代理 URL，避免冷启动先显示 Logo 再换图 */
export function rehydrateSessionAvatar(session: ChatSession): ChatSession {
  const next = { ...session }
  if (!next.isGroup && next.peerUserId) {
    const url = resolveUserAvatarUrl(next.avatarUrl, next.peerUserId)
    if (url) next.avatarUrl = url
  } else if (next.isGroup) {
    if (next.ownerUserId) {
      const ownerUrl = resolveUserAvatarUrl(next.ownerAvatarUrl, next.ownerUserId)
      if (ownerUrl) next.ownerAvatarUrl = ownerUrl
    }
    if (next.avatarUrl) {
      const url = resolveUserAvatarUrl(next.avatarUrl)
      if (url) next.avatarUrl = url
    }
  } else if (next.avatarUrl) {
    const url = resolveUserAvatarUrl(next.avatarUrl)
    if (url) next.avatarUrl = url
  }
  if (next.memberAvatars?.length) {
    next.memberAvatars = next.memberAvatars.map(m => {
      const member = m as { userId?: string }
      const url = resolveUserAvatarUrl(m.imageUrl, member.userId)
      return url ? { ...m, imageUrl: url } : m
    })
  }
  return next
}

function stableSessionAvatarUrl(session: ChatSession): string | undefined {
  if (!session.isGroup && session.peerUserId) {
    return resolveUserAvatarUrl(session.avatarUrl, session.peerUserId) || undefined
  }
  const stripped = stripEphemeralMediaUrl(session.avatarUrl)
  if (stripped) return stripped
  if (session.avatarUrl) {
    return resolveUserAvatarUrl(session.avatarUrl) || undefined
  }
  return undefined
}

function stableOwnerAvatarUrl(session: ChatSession): string | undefined {
  if (!session.isGroup || !session.ownerUserId) return undefined
  return resolveUserAvatarUrl(session.ownerAvatarUrl, session.ownerUserId) || undefined
}

function sanitizeSessionForPersist(session: ChatSession): ChatSession {
  const next: ChatSession = {
    ...session,
    avatarUrl: stableSessionAvatarUrl(session),
    ownerAvatarUrl: stableOwnerAvatarUrl(session)
  }
  if (session.memberAvatars?.length) {
    next.memberAvatars = session.memberAvatars.map(m => {
      const member = m as { userId?: string }
      const imageUrl =
        resolveUserAvatarUrl(stripEphemeralMediaUrl(m.imageUrl) || m.imageUrl, member.userId) ||
        undefined
      return { ...m, imageUrl }
    })
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
    const uid = profile.userId != null ? String(profile.userId) : ''
    if (uid) {
      const stable = resolveUserAvatarUrl(
        typeof profile.avatar === 'string' ? profile.avatar : '',
        uid
      )
      if (stable) profile.avatar = stable
      else if (typeof profile.avatar === 'string') {
        profile.avatar = stripEphemeralMediaUrl(profile.avatar)
      }
    } else if (typeof profile.avatar === 'string') {
      profile.avatar = stripEphemeralMediaUrl(profile.avatar)
    }
    // PII 脱敏：email/phone 不持久化到 localStorage，登录后由接口重新拉取
    delete profile.email
    delete profile.phone
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

/** 通讯录持久化：去掉好友预签名头像与易过期的在线快照 */
export function sanitizeContactsPersistState(state: {
  items?: Array<{ avatarUrl?: string; online?: boolean; [k: string]: unknown }>
}): typeof state {
  if (!Array.isArray(state.items)) return state
  return {
    ...state,
    items: state.items.map(item => {
      const { online: _online, ...rest } = item
      return {
        ...rest,
        avatarUrl: stripEphemeralMediaUrl(item.avatarUrl) || undefined
      }
    })
  }
}
