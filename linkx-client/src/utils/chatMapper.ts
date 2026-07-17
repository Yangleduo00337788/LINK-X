import type { ChatMessage, ChatSession } from '../types'
import type { ConversationItem, MessageItem } from '../types/chat'
import { formatChatTime, formatFileSize } from './chatTime'

const GROUP_COLORS = ['#12b7f5', '#52c41a', '#722ed1', '#fa8c16', '#eb2f96', '#13c2c2']

function pickColor(seed: string): string {
  let hash = 0
  for (let i = 0; i < seed.length; i++) hash += seed.charCodeAt(i)
  return GROUP_COLORS[hash % GROUP_COLORS.length]
}

export function conversationToSession(conv: ConversationItem): ChatSession {
  // 判断是否为群聊
  const isGroup = conv.type === 2

  if (isGroup) {
    const name = conv.name || '群聊'
    return {
      id: String(conv.id),
      name,
      lastMessage: conv.lastMessage || '',
      time: formatChatTime(conv.lastMessageTime),
      avatarText: name.charAt(0) || '群',
      avatarColor: pickColor(name),
      avatarUrl: conv.avatar,
      isGroup: true,
      isReal: true
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
    avatarUrl: conv.peerAvatar,
    peerUserId: conv.peerUserId ? String(conv.peerUserId) : undefined,
    isGroup: false,
    isReal: true
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

  switch (type) {
    case 'file':
      content = message.fileName || message.content || '文件'
      fileStatus = '已发送'
      break
    case 'image':
      content = message.fileUrl || message.content
      isImage = true
      break
    case 'redPacket':
      content = `[红包] ${message.fileName || '恭喜发财'}`
      fileName = message.fileName
      fileUrl = message.fileUrl
      break
  }

  return {
    id: String(message.id),
    sessionId,
    content,
    time: formatChatTime(message.createTime),
    isSelf: message.isSelf ?? false,
    senderName: message.senderNickname,
    senderAvatar: message.senderAvatar,
    type,
    fileName,
    fileSize,
    fileUrl,
    isImage,
    fileStatus
  }
}

export function messagePreviewFromItem(message: MessageItem): string {
  switch (message.type) {
    case 'file': return `[文件] ${message.fileName || message.content}`
    case 'image': return '[图片]'
    case 'redPacket': return '[红包]'
    case 'voice': return '[语音]'
    default: return message.content
  }
}
