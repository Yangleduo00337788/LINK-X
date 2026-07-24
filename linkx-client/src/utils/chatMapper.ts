import type { ChatMessage, ChatSession } from '../types'
import type { ConversationItem, MessageItem } from '../types/chat'
import { formatChatTime, formatFileSize } from './chatTime'
import { normalizeMediaUrl } from './mediaUrl'

const GROUP_COLORS = ['#12b7f5', '#52c41a', '#722ed1', '#fa8c16', '#eb2f96', '#13c2c2']

function pickColor(seed: string): string {
  let hash = 0
  for (let i = 0; i < seed.length; i++) hash += seed.charCodeAt(i)
  return GROUP_COLORS[hash % GROUP_COLORS.length]
}

function mapMemberAvatars(
  list?: Array<{ nickname?: string; avatar?: string }>
): Array<{ text: string; color: string; imageUrl?: string }> | undefined {
  if (!list?.length) return undefined
  return list.slice(0, 9).map(m => {
    const name = m.nickname || '?'
    return {
      text: name.charAt(0) || '?',
      color: pickColor(name),
      imageUrl: normalizeMediaUrl(m.avatar) || undefined
    }
  })
}

export function conversationToSession(conv: ConversationItem): ChatSession {
  // 判断是否为群聊
  const isGroup = conv.type === 2

  if (isGroup) {
    const groupName = conv.name || '群聊'
    const remark = conv.myRemark?.trim() || ''
    const name = remark || groupName
    return {
      id: String(conv.id),
      name,
      groupName,
      groupRemark: remark || undefined,
      lastMessage: conv.lastMessage || '',
      time: formatChatTime(conv.lastMessageTime),
      avatarText: name.charAt(0) || '群',
      avatarColor: pickColor(groupName),
      // 仅自定义群头像；默认用 memberAvatars 拼图
      avatarUrl: normalizeMediaUrl(conv.avatar) || undefined,
      memberAvatars: mapMemberAvatars(conv.memberAvatars),
      isGroup: true,
      isReal: true,
      pinned: !!conv.pinned,
      important: !!conv.important,
      muted: !!conv.muted,
      unread: conv.unreadCount != null ? Number(conv.unreadCount) || undefined : undefined
    }
  }

  // 单聊
  const name = conv.peerRemark || conv.peerNickname || conv.peerUsername || '好友'
  return {
    id: String(conv.id),
    name,
    lastMessage: conv.lastMessage || '',
    time: formatChatTime(conv.lastMessageTime),
    avatarText: name.charAt(0) || '?',
    avatarColor: pickColor(name),
    avatarUrl: normalizeMediaUrl(conv.peerAvatar) || undefined,
    peerUserId: conv.peerUserId ? String(conv.peerUserId) : undefined,
    online: !!conv.peerOnline,
    isGroup: false,
    isReal: true,
    pinned: !!conv.pinned,
    important: !!conv.important,
    muted: !!conv.muted,
    blocked: !!conv.blocked,
    unread: conv.unreadCount != null ? Number(conv.unreadCount) || undefined : undefined
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

  switch (type) {
    case 'file':
      content = message.fileName || message.content || '文件'
      fileStatus = '已发送'
      break
    case 'image':
      content = message.fileUrl || message.content
      isImage = true
      break
    case 'voice':
      content = '[语音]'
      fileUrl = message.fileUrl || message.content
      break
    case 'recall':
      content = '撤回了一条消息'
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
      redPacketGreeting = message.redPacketGreeting ?? message.fileName ?? '恭喜发财'
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
      content = `[红包] ${redPacketGreeting}`
      fileName = redPacketGreeting
      fileUrl = redPacketId
      fileSize = redPacketAmount ? `${redPacketAmount} 元` : undefined
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
    senderAvatar: normalizeMediaUrl(message.senderAvatar) || undefined,
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
    deliveryStatus: message.deliveryStatus,
    edited: message.edited,
    clientMsgId: message.clientMsgId,
    sendStatus: (message.isSelf ?? false) ? mapSendStatus(message.deliveryStatus) : undefined,
    replyTo: buildReplyTo(message, sessionId)
  }
}

function mapSendStatus(
  deliveryStatus?: string
): ChatMessage['sendStatus'] {
  if (deliveryStatus === 'delivered') return 'delivered'
  if (deliveryStatus === 'failed') return 'failed'
  return 'sent'
}

function buildReplyTo(message: MessageItem, sessionId: string): ChatMessage | undefined {
  if (message.quoteMessageId == null && !message.quoteContent) return undefined
  return {
    id: String(message.quoteMessageId ?? ''),
    sessionId,
    content: message.quoteContent || '',
    time: '',
    isSelf: false,
    senderId: message.quoteSenderId != null ? String(message.quoteSenderId) : undefined,
    senderName: undefined,
    type: 'text'
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
    case 'file': return `[文件] ${message.fileName || message.content}`
    case 'image': return '[图片]'
    case 'redPacket': return '[红包]'
    case 'voice': return '[语音]'
    case 'recall': return '撤回了一条消息'
    case 'system': return message.content || '[系统消息]'
    default: return message.content
  }
}
