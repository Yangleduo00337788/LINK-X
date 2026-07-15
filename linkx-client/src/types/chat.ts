export interface ConversationItem {
  id: string
  type: number
  /** 群聊名称（type=2 时使用） */
  name?: string
  /** 群头像（type=2 时使用） */
  avatar?: string
  peerUserId?: string
  peerUsername?: string
  peerNickname?: string
  peerAvatar?: string
  peerRemark?: string
  lastMessage?: string
  lastMessageTime?: string | number
}

export interface MessageItem {
  id: string
  conversationId: string
  senderId: string
  senderNickname?: string
  senderAvatar?: string
  type: 'text' | 'image' | 'file' | 'voice' | 'redPacket'
  content: string
  fileName?: string
  fileSize?: string | number
  fileUrl?: string
  voiceDuration?: number
  createTime?: string | number
  isSelf?: boolean
}

export interface ChatFileUploadResult {
  url: string
  fileName?: string
  fileSize?: string | number
  contentType?: string
}

export interface WsSendPayload {
  action: 'send'
  clientMsgId: string
  conversationId: string
  msgType: 'text' | 'image' | 'file' | 'voice'
  content?: string
  fileName?: string
  fileSize?: string | number
  fileUrl?: string
  voiceDuration?: number
}

export interface WsIncomingFrame {
  action: 'message' | 'ack' | 'pong' | 'error'
  clientMsgId?: string
  code?: number
  message?: string
  data?: MessageItem
}
