export type NavKey = 'chat' | 'contacts' | 'favorites' | 'files' | 'moments' | 'apps'

export type OverlayPage =
  | 'settings'
  | 'notifications'
  | 'privacy'
  | 'help'
  | 'about'
  | 'profile'
  | 'add-friend'
  | 'create-group'
  | 'create-channel'
  | 'weather'
  | 'app-runner'
  | 'file-preview'
  | 'chat-history'

export interface ChatSession {
  id: string
  name: string
  lastMessage: string
  time: string
  avatarText: string
  avatarColor: string
  unread?: number
  muted?: boolean
  pinned?: boolean
  blocked?: boolean
  isGroup?: boolean
  /** 好友在线状态（单聊） */
  online?: boolean
  avatarUrl?: string
}

export interface ChatMessage {
  id: string
  sessionId: string
  content: string
  time: string
  isSelf: boolean
  senderName?: string
  senderAvatar?: string
  type?: 'text' | 'image' | 'file' | 'link' | 'system' | 'voice' | 'redPacket'
  linkUrl?: string
  
  // 对于 file 类型的扩展
  fileName?: string
  fileSize?: string
  fileUrl?: string
  /** 文件卡片底栏：已发送 / 已下载 */
  fileStatus?: string
  
  // 对于 image 类型的扩展
  isImage?: boolean

  /** 语音消息时长（秒） */
  voiceDuration?: number
  voiceUrl?: string

  /** 红包 */
  redPacketGreeting?: string
  redPacketAmount?: string
  redPacketOpened?: boolean
  
  // 消息引用
  replyTo?: ChatMessage
}

export interface ContactItem {
  id: string
  name: string
  avatarText: string
  avatarColor: string
  group: string
  online?: boolean
}

export interface FavoriteItem {
  id: string
  title: string
  preview: string
  time: string
  type: 'note' | 'image' | 'link' | 'file'
}

export interface AppItem {
  id: string
  name: string
  desc: string
  icon: string
  color: string
  /** 内嵌 WebView / iframe 地址 */
  url?: string
}

export type ChatBackgroundId = 'default' | 'purple' | 'orange'

export interface SidebarItem {
  key: string
  icon: string
  badge?: number
}