export interface ConversationItem {
  id: string
  type: number
  /** 群聊名称（type=2 时使用） */
  name?: string
  /** 当前用户对本群备注（仅自己可见） */
  myRemark?: string
  /** 群头像（type=2 时使用；自定义上传时才有） */
  avatar?: string
  /** 群成员头像预览（拼图用） */
  memberAvatars?: Array<{ nickname?: string; avatar?: string }>
  peerUserId?: string
  peerUsername?: string
  peerNickname?: string
  peerAvatar?: string
  peerRemark?: string
  /** 单聊对方是否在线（受对方隐私「在线状态可见」约束） */
  peerOnline?: boolean
  lastMessage?: string
  lastMessageTime?: string | number
  pinned?: boolean
  muted?: boolean
  unreadCount?: number
}

export interface MessageItem {
  id: string
  conversationId: string
  senderId: string
  senderNickname?: string
  senderAvatar?: string
  type: 'text' | 'image' | 'file' | 'voice' | 'redPacket' | 'recall' | 'system'
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
  deliveryStatus?: string
  edited?: boolean
  editedTime?: string | number
  quoteMessageId?: string | number
  quoteContent?: string
  quoteSenderId?: string | number
  clientMsgId?: string
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
  quoteMessageId?: string
}

export interface WsIncomingFrame {
  action:
    | 'message'
    | 'ack'
    | 'pong'
    | 'error'
    | 'recall'
    | 'edit'
    | 'deliveryReceipt'
    | 'readReceipt'
    | 'call_invite'
    | 'call_accept'
    | 'call_reject'
    | 'call_cancel'
    | 'call_hangup'
    | 'call_signal'
    | 'notification_refresh'
    | string
  clientMsgId?: string
  code?: number
  message?: string
  data?: MessageItem | Record<string, unknown>
}
