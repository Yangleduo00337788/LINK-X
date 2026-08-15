/**
 * LinkX 客服账号标识（与后端 linkx_cs 系统用户对应）。
 */
import type { ChatSession } from '../types'
import { DEFAULT_AVATAR_URL } from './defaultAvatar'
import { t } from '../i18n'

export const CUSTOMER_SERVICE_USERNAME = 'linkx_cs'

export function isCustomerServiceUsername(username?: string | null): boolean {
  return (username || '').trim().toLowerCase() === CUSTOMER_SERVICE_USERNAME
}

export function isCustomerServicePeer(peerUserId?: string | null, peerUsername?: string | null): boolean {
  return isCustomerServiceUsername(peerUsername)
}

export function customerServiceDisplayName(): string {
  return t('chat.customerServiceName')
}

export function customerServiceAvatarUrl(): string {
  return DEFAULT_AVATAR_URL
}

/** 在会话列表中查找 LinkX 客服会话 */
export function findCustomerServiceSession(sessions: ChatSession[]): ChatSession | undefined {
  const displayName = customerServiceDisplayName()
  return sessions.find(
    s =>
      s.isReal &&
      !s.isGroup &&
      (isCustomerServicePeer(s.peerUserId, s.peerUsername) || s.name === displayName)
  )
}
