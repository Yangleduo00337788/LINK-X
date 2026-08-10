/**
 * 作者：yangleduo
 */
import { t } from '../i18n'
import type { ChatMessage } from '../types'

/** 会话列表/持久化占位：图片消息摘要 */
export function imagePreviewPlaceholder(): string {
  return t('chat.preview.image')
}

export function filePreviewLabel(name?: string): string {
  return t('chat.preview.file', { name: name || '' })
}

export function voicePreviewLabel(): string {
  return t('chat.preview.voice')
}

export function locationPreviewLabel(content?: string): string {
  return t('chat.preview.location', { content: content || '' })
}

export function redPacketPreviewLabel(greeting?: string): string {
  return t('chat.preview.redPacket', { greeting: greeting || t('modals.greetingFallback') })
}

export function voiceCallPreviewLabel(): string {
  return t('chat.preview.voiceCall')
}

export function videoCallPreviewLabel(): string {
  return t('chat.preview.videoCall')
}

export function meetingPreviewLabel(): string {
  return t('chat.preview.meeting')
}

export function multiMeetingPreviewLabel(): string {
  return t('chat.preview.multiMeeting')
}

export function recalledPreviewLabel(): string {
  return t('chat.preview.recalled')
}

export function systemPreviewLabel(content?: string): string {
  return content || t('chat.preview.system')
}

/** 转发弹窗中的消息摘要 */
export function chatMessagePreviewText(msg: ChatMessage): string {
  if (msg.type === 'file') return filePreviewLabel(msg.fileName || msg.content)
  if (msg.type === 'image' || msg.isImage) return imagePreviewPlaceholder()
  if (msg.type === 'voice') return voicePreviewLabel()
  if (msg.type === 'location') return locationPreviewLabel(msg.content || '')
  if (msg.type === 'redPacket') return redPacketPreviewLabel(msg.redPacketGreeting)
  if (msg.type === 'conference') {
    const content = (msg.content || '').trim()
    if (content) return content
    return meetingPreviewLabel()
  }
  if (msg.type === 'recall') return recalledPreviewLabel()
  if (msg.type === 'system') return systemPreviewLabel(msg.content)
  return (msg.content || '').trim()
}
