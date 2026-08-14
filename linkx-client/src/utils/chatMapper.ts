/**
 * 作者：yangleduo
 */
import type { ChatMessage, ChatSession } from '../types'
import type { ConversationItem, MessageItem } from '../types/chat'
import { formatChatTime, formatFileSize } from './chatTime'
import { normalizeMediaUrl } from './mediaUrl'
import { resolveUserAvatarUrl } from './defaultAvatar'
import { t } from '../i18n'
import { formatFriendDisplayName, friendAvatarText } from './friendDisplay'
import {
  filePreviewLabel,
  imagePreviewPlaceholder,
  locationPreviewLabel,
  meetingPreviewLabel,
  multiMeetingPreviewLabel,
  recalledPreviewLabel,
  redPacketPreviewLabel,
  systemPreviewLabel,
  videoCallPreviewLabel,
  voiceCallPreviewLabel,
  voicePreviewLabel
} from './messagePreviewText'

const GROUP_COLORS = ['#12b7f5', '#52c41a', '#722ed1', '#fa8c16', '#eb2f96', '#13c2c2']

function pickColor(seed: string): string {
  let hash = 0
  for (let i = 0; i < seed.length; i++) hash += seed.charCodeAt(i)
  return GROUP_COLORS[hash % GROUP_COLORS.length]
}

export function conversationToSession(conv: ConversationItem): ChatSession {
  const isGroup = conv.type === 2

  if (isGroup) {
    const groupName = conv.name || t('modals.groupChat')
    const remark = conv.myRemark?.trim() || ''
    const name = remark || groupName
    const ownerId = conv.ownerId != null ? String(conv.ownerId) : undefined
    const ownerPreview = conv.memberAvatars?.[0]?.avatar
    return {
      id: String(conv.id),
      name,
      groupName,
      groupRemark: remark || undefined,
      lastMessage: conv.lastMessage || '',
      time: formatChatTime(conv.lastMessageTime),
      avatarText: name.charAt(0) || t('defaults.groupChar'),
      avatarColor: pickColor(groupName),
      avatarUrl: normalizeMediaUrl(conv.avatar) || undefined,
      ownerUserId: ownerId,
      ownerAvatarUrl: ownerId
        ? resolveUserAvatarUrl(ownerPreview, ownerId) || undefined
        : undefined,
      isGroup: true,
      isReal: true,
      pinned: !!conv.pinned,
      important: !!conv.important,
      muted: !!conv.muted,
      unread:
        conv.unreadCount != null
          ? Math.max(0, Number(conv.unreadCount) || 0)
          : undefined
    }
  }

  // 单聊
  const nickname = conv.peerNickname || conv.peerUsername || t('defaults.friend')
  const remark = conv.peerRemark?.trim() || ''
  const name = formatFriendDisplayName(nickname, remark)
  return {
    id: String(conv.id),
    name,
    lastMessage: conv.lastMessage || '',
    time: formatChatTime(conv.lastMessageTime),
    avatarText: friendAvatarText(nickname, remark),
    avatarColor: pickColor(nickname),
    avatarUrl: resolveUserAvatarUrl(conv.peerAvatar, conv.peerUserId),
    peerUserId: conv.peerUserId ? String(conv.peerUserId) : undefined,
    online: !!conv.peerOnline,
    isGroup: false,
    isReal: true,
    pinned: !!conv.pinned,
    important: !!conv.important,
    muted: !!conv.muted,
    blocked: !!conv.blocked,
    unread:
      conv.unreadCount != null
        ? Math.max(0, Number(conv.unreadCount) || 0)
        : undefined
  }
}

export function messageToChatMessage(message: MessageItem, sessionId: string): ChatMessage {
  const type = message.type
  let content = message.content
  let fileName = message.fileName
  let fileUrl = message.fileUrl
  let fileSize = message.fileSize ? formatFileSize(message.fileSize) : undefined
  let isImage = type === 'image'
  let fileStatus: string | undefined

  // 红包相关字段（优先使用服务端语义化字段，否则从通用字段反推）
  let redPacketGreeting: string | undefined
  let redPacketAmount: string | undefined
  let redPacketId: string | undefined
  let redPacketType: 'normal' | 'lucky' | undefined
  let redPacketTotalCount: number | undefined
  let redPacketRemainingCount: number | undefined
  let redPacketReceived: boolean | undefined
  let redPacketReceivedAmount: string | undefined
  let redPacketStatus: 'active' | 'finished' | 'expired' | undefined
  let conferenceId: string | undefined
  let conferenceTitle: string | undefined
  let conferenceHasPassword: boolean | undefined
  let conferenceType: 'voice' | 'video' | undefined
  let conferenceScene: 'call' | 'meeting' | undefined

  switch (type) {
    case 'file':
      content = message.fileName || message.content || t('chat.fileFallback')
      fileStatus = message.isSelf ? t('chat.fileStatusSent') : t('chat.fileStatusReceived')
      break
    case 'image': {
      const imageSrc = normalizeMediaUrl(message.fileUrl || message.content) || ''
      content = imageSrc
      fileUrl = imageSrc || fileUrl
      isImage = true
      break
    }
    case 'voice':
      content = voicePreviewLabel()
      fileUrl = message.fileUrl || message.content
      break
    case 'recall':
      content = recalledPreviewLabel()
      isImage = false
      fileUrl = undefined
      fileName = undefined
      fileSize = undefined
      break
    case 'system':
      isImage = false
      fileUrl = undefined
      fileName = undefined
      fileSize = undefined
      break
    case 'redPacket': {
      // 服务端下行时已经把红包专属字段填到 message 上；若未填，从通用字段反推
      redPacketId = message.redPacketId ?? message.fileUrl ?? undefined
      redPacketGreeting = message.redPacketGreeting ?? message.fileName ?? t('extra.greetingFallback')
      const rawTotal = message.redPacketTotalAmount ?? message.fileSize
      // 后端约定 fileSize 为「分」，totalAmount 也可能是「分」；用 toYuan 统一展示
      redPacketAmount = rawTotal != null ? formatYuan(rawTotal) : ''
      redPacketType = message.redPacketType
      redPacketTotalCount = message.redPacketTotalCount
      redPacketRemainingCount = message.redPacketRemainingCount
      redPacketReceived = message.redPacketReceived
      const rawRecv = message.redPacketReceivedAmount
      redPacketReceivedAmount = rawRecv != null ? formatYuan(rawRecv) : undefined
      redPacketStatus = message.redPacketStatus ?? 'active'
      content = t('chat.redPacketBracket', { greeting: redPacketGreeting })
      fileName = redPacketGreeting
      fileUrl = redPacketId
      fileSize = redPacketAmount ? t('chat.currencyYuan', { amount: redPacketAmount }) : undefined
      break
    }
    case 'conference': {
      conferenceId = message.conferenceId ?? message.fileUrl ?? undefined
      conferenceTitle = message.conferenceTitle ?? message.fileName ?? multiMeetingPreviewLabel()
      const rawPwd = message.conferenceHasPassword
      conferenceHasPassword =
        rawPwd === true ||
        Number(message.fileSize) === 1 ||
        String(message.fileSize) === '1'
      content = message.content || t('chat.meetingBracket', { title: conferenceTitle })
      // 文案区分：语音通话 / 视频通话 / 会议
      const rawType = (message as { conferenceType?: string }).conferenceType
      const rawScene = (message as { conferenceScene?: string }).conferenceScene
      if (rawType === 'voice' || rawType === 'video') {
        conferenceType = rawType
      } else if (/语音通话|语音会议|voice\s*call/i.test(content)) {
        conferenceType = 'voice'
      } else {
        conferenceType = 'video'
      }
      if (rawScene === 'call' || rawScene === 'meeting') {
        conferenceScene = rawScene
      } else if (/语音通话|视频通话|voice\s*call|video\s*call/i.test(content)) {
        conferenceScene = 'call'
      } else {
        conferenceScene = 'meeting'
      }
      fileName = conferenceTitle
      fileUrl = conferenceId
      fileSize = undefined
      break
    }
  }

  return {
    id: String(message.id),
    sessionId,
    content,
    time: formatChatTime(message.createTime),
    createTime:
      typeof message.createTime === 'number'
        ? message.createTime
        : message.createTime
          ? Number(message.createTime) || undefined
          : undefined,
    isSelf: message.isSelf ?? false,
    senderId: message.senderId ? String(message.senderId) : undefined,
    senderName: message.senderNickname,
    senderAvatar: resolveUserAvatarUrl(message.senderAvatar, message.senderId),
    type,
    fileName,
    fileSize,
    fileUrl,
    isImage,
    fileStatus,
    voiceDuration: type === 'voice' ? message.voiceDuration : undefined,
    voiceUrl:
      type === 'voice'
        ? normalizeMediaUrl(message.fileUrl || message.content) || undefined
        : undefined,
    redPacketGreeting,
    redPacketAmount,
    redPacketId,
    redPacketType,
    redPacketTotalCount,
    redPacketRemainingCount,
    redPacketReceived,
    redPacketReceivedAmount,
    redPacketStatus,
    redPacketOpened: type === 'redPacket' ? !!redPacketReceived : undefined,
    conferenceId,
    conferenceTitle,
    conferenceHasPassword,
    conferenceType,
    conferenceScene,
    deliveryStatus: message.deliveryStatus,
    edited: message.edited,
    clientMsgId: message.clientMsgId,
    sensitiveAlert: !!message.sensitiveAlert,
    sendStatus: (message.isSelf ?? false) ? mapSendStatus(message.deliveryStatus) : undefined,
    replyTo: buildReplyTo(message, sessionId)
  }
}

function mapSendStatus(
  deliveryStatus?: string
): ChatMessage['sendStatus'] {
  if (deliveryStatus === 'read') return 'read'
  if (deliveryStatus === 'delivered') return 'delivered'
  if (deliveryStatus === 'failed') return 'failed'
  return 'sent'
}

function buildReplyTo(message: MessageItem, sessionId: string): ChatMessage | undefined {
  if (message.quoteMessageId == null && !message.quoteContent) return undefined
  const quoteType = (message.quoteType || 'text') as ChatMessage['type']
  return {
    id: String(message.quoteMessageId ?? ''),
    sessionId,
    content: message.quoteContent || '',
    time: '',
    isSelf: false,
    senderId: message.quoteSenderId != null ? String(message.quoteSenderId) : undefined,
    senderName: undefined,
    type: quoteType,
    isImage: quoteType === 'image'
  }
}

/**
 * 把「分」或元数值的金额格式化为带两位小数的字符串。
 * 当输入为字符串且不含小数点、长度 ≤ 5 时按「分」处理（与后端约定）。
 */
function formatYuan(value: string | number): string {
  if (typeof value === 'number') {
    return value.toFixed(2)
  }
  const s = String(value).trim()
  if (!s) return ''
  if (s.includes('.')) return s
  const asNumber = Number(s)
  if (!Number.isFinite(asNumber)) return s
  // 整数且长度合理时按「分」处理
  if (/^\d{1,6}$/.test(s)) {
    return (asNumber / 100).toFixed(2)
  }
  return s
}

export function messagePreviewFromItem(message: MessageItem): string {
  switch (message.type) {
    case 'file':
      return filePreviewLabel(message.fileName || message.content)
    case 'image':
      return imagePreviewPlaceholder()
    case 'redPacket':
      return redPacketPreviewLabel()
    case 'conference': {
      const content = (message.content || '').trim()
      if (/语音通话|Voice call/i.test(content) || message.fileName === '语音通话') {
        return `[${voiceCallPreviewLabel()}] ${message.fileName || message.conferenceTitle || voiceCallPreviewLabel()}`
      }
      if (/视频通话|Video call/i.test(content) || message.fileName === '视频通话') {
        return `[${videoCallPreviewLabel()}] ${message.fileName || message.conferenceTitle || videoCallPreviewLabel()}`
      }
      const scene = (message as { conferenceScene?: string }).conferenceScene
      const type = (message as { conferenceType?: string }).conferenceType
      if (scene === 'call') {
        const kind = type === 'voice' ? voiceCallPreviewLabel() : videoCallPreviewLabel()
        return `[${kind}] ${message.fileName || message.conferenceTitle || kind}`
      }
      return `[${meetingPreviewLabel()}] ${message.fileName || message.conferenceTitle || multiMeetingPreviewLabel()}`
    }
    case 'voice':
      return voicePreviewLabel()
    case 'location':
      return locationPreviewLabel(message.content || '')
    case 'recall':
      return recalledPreviewLabel()
    case 'system':
      return systemPreviewLabel(message.content)
    default:
      return message.content
  }
}
