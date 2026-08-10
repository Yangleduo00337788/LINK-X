/**
 * 作者：yangleduo
 */
/** 好友/群邀请通知的统一状态码（与 UI 语言无关） */
export type InviteStatus = 'pending' | 'accepted' | 'rejected' | 'expired'

export const INVITE_STATUS = {
  PENDING: 'pending',
  ACCEPTED: 'accepted',
  REJECTED: 'rejected',
  EXPIRED: 'expired'
} as const satisfies Record<string, InviteStatus>
