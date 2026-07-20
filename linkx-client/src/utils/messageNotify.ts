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
import { t } from '../i18n'

export interface IncomingNotifyContext {
  message: MessageItem
  session: ChatSession | undefined
  sessionId: string
  currentSessionId: string | null
  myNickname?: string
  myUsername?: string
}

/** 文本是否 @ 到指定用户名/昵称，或 @所有人 */
export function contentMentionsUser(
  content: string | undefined,
  names: Array<string | undefined | null>
): boolean {
  if (!content) return false
  if (/(^|[\s\u3000])@(所有人|全体成员|everyone|all)\b/i.test(content) || content.includes('@所有人')) {
    return true
  }
  for (const name of names) {
    const n = (name || '').trim()
    if (!n) continue
    if (content.includes(`@${n}`)) return true
  }
  return false
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

  void showChatDesktopNotification(title, body, !settings.notifySound)
}

async function showChatDesktopNotification(
  title: string,
  body: string,
  silent: boolean
): Promise<void> {
  try {
    if (window.electronAPI?.showNotification) {
      await window.electronAPI.showNotification({ title, body, silent })
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
