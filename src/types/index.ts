export type NavKey = 'chat' | 'contacts' | 'favorites' | 'moments' | 'apps' | 'menu'

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
  type?: 'text' | 'image' | 'file' | 'link' | 'system'
  fileName?: string
  fileSize?: string
  linkUrl?: string
  /** 文件卡片底栏：已发送 / 已下载 */
  fileStatus?: string
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

export interface ChannelItem {
  id: string
  name: string
  desc: string
  members: number
  avatarText: string
  avatarColor: string
  joined?: boolean
}

export interface AppItem {
  id: string
  name: string
  desc: string
  icon: string
  color: string
}

export interface SidebarItem {
  key: string
  icon: string
  badge?: number
}