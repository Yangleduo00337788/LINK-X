/**
 * 作者：yangleduo
 */
/**
 * 新消息提醒：按「消息通知」偏好播放提示音 / 弹出桌面通知。
 *
 * 规则摘要：
 * - 自己发的消息不提醒
 * - 正在看的会话（前台可见）不提醒
 * - 免会话：默认不提醒；群聊开启「群聊 @ 我」且正文 @ 到自己（或 @所有人）时仍提醒
 * - soundNotify + notifyTone → 应用内合成音
 * - 窗口在后台时 → 桌面通知；messageDetail 控制正文；notifySound 控制系统通知是否发声
 */

import type { ChatSession } from '../types'
import type { MessageItem } from '../types/chat'
import { messagePreviewFromItem } from './chatMapper'
import { playTone, type ToneId } from './notifyTone'
import { useAppSettingsStore } from '../stores/appSettings'
import { isInQuietHours } from './notifyAggregate'
import { t } from '../i18n'

export type DesktopNotificationAction = {
  kind: 'session' | 'official' | 'contacts' | 'calendar' | 'moments' | 'focus'
  sessionId?: string
  notificationId?: string
  avatarUrl?: string
}

export interface IncomingNotifyContext {
  message: MessageItem
  session: ChatSession | undefined
  sessionId: string
  currentSessionId: string | null
  myNickname?: string
  myUsername?: string
}

/** 匹配消息中的 @提及（全体成员 / 所有人 / 连续非空白昵称） */
export const MENTION_TOKEN_RE = /@(?:全体成员|所有人|everyone|all|[^\s@]+)/gi

/** 文本是否 @ 到指定用户名/昵称，或 @所有人/@全体成员 */
export function contentMentionsUser(
  content: string | undefined,
  names: Array<string | undefined | null>
): boolean {
  if (!content) return false
  if (
    content.includes('@所有人') ||
    content.includes('@全体成员') ||
    /(^|[\s\u3000])@(everyone|all)\b/i.test(content)
  ) {
    return true
  }
  for (const name of names) {
    const n = (name || '').trim()
    if (!n) continue
    if (content.includes(`@${n}`)) return true
  }
  return false
}

export interface MentionSegment {
  text: string
  /** 是否为 @ 片段 */
  mention?: boolean
  /** 是否 @ 到自己或全体 */
  atMe?: boolean
}

/** 将正文拆成普通文本与 @ 片段，供气泡高亮渲染 */
export function splitMentionContent(
  content: string,
  myNames: Array<string | undefined | null> = []
): MentionSegment[] {
  if (!content) return []
  const names = myNames.map(n => (n || '').trim()).filter(Boolean)
  const re = new RegExp(MENTION_TOKEN_RE.source, 'gi')
  const parts: MentionSegment[] = []
  let last = 0
  let m: RegExpExecArray | null
  while ((m = re.exec(content)) !== null) {
    if (m.index > last) {
      parts.push({ text: content.slice(last, m.index) })
    }
    const full = m[0]
    const name = full.slice(1)
    const isAtAll = /^(全体成员|所有人|everyone|all)$/i.test(name)
    const isAtMe = isAtAll || names.some(n => name === n)
    parts.push({ text: full, mention: true, atMe: isAtMe })
    last = m.index + full.length
  }
  if (last < content.length) {
    parts.push({ text: content.slice(last) })
  }
  return parts.length ? parts : [{ text: content }]
}

/** 当前是否正在前台查看该会话 */
export function isActivelyViewingSession(
  sessionId: string,
  currentSessionId: string | null
): boolean {
  if (currentSessionId !== sessionId) return false
  if (typeof document === 'undefined') return false
  return document.visibilityState === 'visible'
}

/** 窗口是否在后台（隐藏或未聚焦） */
export function isWindowInBackground(): boolean {
  if (typeof document === 'undefined') return true
  return document.visibilityState === 'hidden' || !document.hasFocus()
}

function isQuietNow(settings: ReturnType<typeof useAppSettingsStore>): boolean {
  return isInQuietHours(
    new Date(),
    !!settings.quietHoursEnabled,
    settings.quietHoursStart || '22:00',
    settings.quietHoursEnd || '08:00'
  )
}

/**
 * 免打扰会话是否仍应提醒（群聊 @ 我）。
 * 非静音会话恒为 true。
 */
export function shouldAlertForSession(
  session: ChatSession | undefined,
  message: MessageItem,
  opts: { notifyAtMe: boolean; myNickname?: string; myUsername?: string }
): boolean {
  if (!session?.muted) return true
  if (!session.isGroup || !opts.notifyAtMe) return false
  return contentMentionsUser(message.content, [opts.myNickname, opts.myUsername])
}

/** 收到新消息后按偏好提醒（声音 / 桌面通知） */
export function notifyIncomingMessage(ctx: IncomingNotifyContext): void {
  const { message, session, sessionId, currentSessionId, myNickname, myUsername } = ctx
  if (message.isSelf) return

  const settings = useAppSettingsStore()
  if (settings.notifyChat === false) return
  if (isQuietNow(settings)) return
  if (
    !shouldAlertForSession(session, message, {
      notifyAtMe: settings.notifyAtMe,
      myNickname,
      myUsername
    })
  ) {
    return
  }

  if (isActivelyViewingSession(sessionId, currentSessionId)) return

  if (settings.soundNotify) {
    playTone((settings.notifyTone || 'default') as ToneId)
  }

  if (!isWindowInBackground()) return

  const title = session?.name?.trim() || 'LinkX'
  const preview = messagePreviewFromItem(message).trim()
  const body = settings.messageDetail
    ? preview || t('notifications.newMessageGeneric')
    : t('notifications.newMessageGeneric')

  void showChatDesktopNotification(title, body, !settings.notifySound, {
    kind: 'session',
    sessionId,
    avatarUrl: session?.avatarUrl || undefined
  })
}

/**
 * 好友申请 / 群邀请等社交通知：按「新消息声音」偏好播提示音，后台时桌面通知。
 */
export function notifySocialEvent(kind: 'friend_request' | 'group_invitation'): void {
  const settings = useAppSettingsStore()
  if (settings.notifySocial === false) return
  if (isQuietNow(settings)) return
  if (settings.soundNotify) {
    playTone((settings.notifyTone || 'default') as ToneId)
  }

  if (!isWindowInBackground()) return

  const title = 'LinkX'
  const body =
    kind === 'friend_request'
      ? t('notifications.friendRequestAlert')
      : t('notifications.groupInviteAlert')
  void showChatDesktopNotification(title, body, !settings.notifySound, { kind: 'contacts' })
}

const MOMENTS_NOTIFY_TYPES = new Set([
  'moments_like',
  'moments_comment',
  'moments_mention',
  'moments_at'
])

/**
 * 友链点赞 / 评论 / @：按「友链提醒」偏好播提示音，后台时桌面通知。
 * 列表与角标仍由 notifications store 刷新，本函数只控制声音与桌面弹窗。
 */
export function notifyMomentsEvent(type: string): void {
  if (!MOMENTS_NOTIFY_TYPES.has(type)) return
  const settings = useAppSettingsStore()
  if (settings.notifyMoments === false) return
  if (isQuietNow(settings)) return

  if (settings.soundNotify) {
    playTone((settings.notifyTone || 'default') as ToneId)
  }

  if (!isWindowInBackground()) return

  const bodyByType: Record<string, string> = {
    moments_like: t('moments.likedYour'),
    moments_comment: t('moments.commentedYour'),
    moments_mention: t('moments.mentionedYou'),
    moments_at: t('moments.mentionedYou')
  }
  const title = t('notifications.momentsAlertTitle')
  const body = settings.messageDetail
    ? bodyByType[type] || t('moments.newNotif')
    : t('moments.newNotif')
  void showChatDesktopNotification(title, body, !settings.notifySound, { kind: 'moments' })
}

const SHORT_VIDEO_NOTIFY_TYPES = new Set([
  'short_video_like',
  'short_video_comment',
  'short_video_mention'
])

/** 短视频点赞 / 评论：复用友链提醒偏好。 */
export function notifyShortVideoEvent(type: string): void {
  if (!SHORT_VIDEO_NOTIFY_TYPES.has(type)) return
  const settings = useAppSettingsStore()
  if (settings.notifyMoments === false) return
  if (isQuietNow(settings)) return

  if (settings.soundNotify) {
    playTone((settings.notifyTone || 'default') as ToneId)
  }

  if (!isWindowInBackground()) return

  const bodyByType: Record<string, string> = {
    short_video_like: t('shortVideo.likedYour'),
    short_video_comment: t('shortVideo.commentedYour'),
    short_video_mention: t('shortVideo.mentionedYou')
  }
  const title = t('shortVideo.notifyTitle')
  const body = settings.messageDetail
    ? bodyByType[type] || t('shortVideo.newNotif')
    : t('shortVideo.newNotif')
  void showChatDesktopNotification(title, body, !settings.notifySound, { kind: 'moments' })
}

/** LinkX 官方反馈进度：声音 + 后台桌面通知 */
export async function notifyOfficialFeedback(type: string, content?: string): Promise<void> {
  const settings = useAppSettingsStore()
  if (settings.notifySystem === false) return
  if (isQuietNow(settings)) return

  try {
    const { useAppStore } = await import('../stores/app')
    const { OFFICIAL_NOTIFY_SESSION_ID } = await import('../types')
    const app = useAppStore()
    if (isActivelyViewingSession(OFFICIAL_NOTIFY_SESSION_ID, app.currentSessionId)) {
      return
    }
  } catch {
    /* ignore */
  }

  if (settings.soundNotify) {
    playTone((settings.notifyTone || 'default') as ToneId)
  }

  const title = t('chat.officialSession')
  const bodyByType: Record<string, string> = {
    feedback_submitted: t('chat.officialSubmittedAlert'),
    feedback_replied: t('chat.officialRepliedAlert'),
    feedback_closed: t('chat.officialClosedAlert'),
    feedback_reopened: t('chat.officialReopenedAlert'),
    review_approved: t('chat.officialReviewApprovedAlert'),
    review_rejected: t('chat.officialReviewRejectedAlert'),
    notice_published: t('chat.officialNoticeAlert')
  }
  const body = settings.messageDetail
    ? (content || '').trim() || bodyByType[type] || t('chat.officialUpdateAlert')
    : bodyByType[type] || t('chat.officialUpdateAlert')

  if (!isWindowInBackground()) return

  void showChatDesktopNotification(title, body, !settings.notifySound, { kind: 'official' })
}

export function notifyFriendOnline(friendName: string, avatarUrl?: string): void {
  const settings = useAppSettingsStore()
  if (settings.notifyFriendOnline === false) return
  if (isQuietNow(settings)) return

  const name = (friendName || '').trim() || t('chat.me')
  const body = t('notifications.friendOnlineAlert', { name })

  if (settings.soundNotify) {
    playTone((settings.notifyTone || 'default') as ToneId)
  }

  // 前台：应用内 toast；后台：桌面通知
  if (!isWindowInBackground()) {
    if (typeof window !== 'undefined') {
      window.dispatchEvent(
        new CustomEvent('linkx:friend-online', {
          detail: { title: name, body, avatarUrl }
        })
      )
    }
    return
  }

  void showChatDesktopNotification(name, body, !settings.notifySound, { kind: 'contacts' })
}

/** 日程提醒：应用内 toast + 提示音；后台时桌面通知 */
let lastCalendarNotifyKey = ''
let lastCalendarNotifyAt = 0

export function notifyCalendarRemind(body: string, phase: 'ahead' | 'start' = 'ahead'): void {
  const dedupeKey = `${phase}:${body}`
  const now = Date.now()
  if (dedupeKey === lastCalendarNotifyKey && now - lastCalendarNotifyAt < 3000) {
    return
  }
  lastCalendarNotifyKey = dedupeKey
  lastCalendarNotifyAt = now

  const settings = useAppSettingsStore()
  if (settings.notifySystem === false) return
  if (isQuietNow(settings)) return

  if (settings.soundNotify) {
    playTone((settings.notifyTone || 'default') as ToneId)
  }

  const title =
    phase === 'start' ? t('calendar.remindStartedTitle') : t('chat.calendarRemind')
  const content = (body || '').trim() || title

  if (!isWindowInBackground()) {
    if (typeof window !== 'undefined') {
      window.dispatchEvent(
        new CustomEvent('linkx:calendar-remind', {
          detail: { title, body: content }
        })
      )
    }
    return
  }

  void showChatDesktopNotification(title, content, !settings.notifySound, { kind: 'calendar' })
}

async function showChatDesktopNotification(
  title: string,
  body: string,
  silent: boolean,
  action?: DesktopNotificationAction
): Promise<void> {
  try {
    if (window.electronAPI?.showNotification) {
      await window.electronAPI.showNotification({ title, body, silent, action })
      return
    }
  } catch (e) {
    console.warn('[messageNotify] Electron 通知失败:', e)
  }

  // Web 兜底（需用户已授权）
  if (typeof Notification === 'undefined') return
  if (Notification.permission !== 'granted') return
  try {
    new Notification(title, { body, silent })
  } catch {
    /* ignore */
  }
}
