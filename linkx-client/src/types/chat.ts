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
  /**
   * 红包专属元数据（来自服务端 MessageVO 透传）。
   * <p>
   * 红包消息的真实载荷按后端约定复用通用字段：
   * {@code fileUrl} = 红包 ID；{@code fileName} = 祝福语；{@code fileSize} = 红包总金额（分）。
   * 为了避免前端各处重复换算，服务端在下行时直接给出语义化字段，前端优先使用；
   * 缺失时再回退到 {@code fileUrl/fileName/fileSize}。
   * </p>
   */
  redPacketId?: string
  redPacketGreeting?: string
  redPacketTotalAmount?: string | number
  redPacketType?: 'normal' | 'lucky'
  redPacketTotalCount?: number
  redPacketRemainingCount?: number
  redPacketReceived?: boolean
  redPacketReceivedAmount?: string | number
  redPacketStatus?: 'active' | 'finished' | 'expired'
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
