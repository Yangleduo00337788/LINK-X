/**
 * 全局业务类型定义。
 * 聊天、联系人、收藏、应用等模块的 TypeScript 接口与字面量类型。
 */

// 左侧 Sidebar 导航项 key，决定主内容区展示哪个模块
export type NavKey = 'chat' | 'contacts' | 'favorites' | 'files' | 'calendar' | 'moments' | 'settings'

/** 设置页分类（左侧导航 / 右侧锚点） */
export type SettingsTab =
  | 'account'
  | 'general'
  | 'notifications'
  | 'privacy'
  | 'chat'
  | 'files'
  | 'shortcuts'
  | 'appearance'
  | 'about'

// 全屏 Overlay 页面标识，由 overlay store 栈管理
export type OverlayPage =
  | 'help'           // 帮助页
  | 'file-preview'   // 文件预览
  | 'chat-history'   // 聊天记录

/** 聊天会话（单聊或群聊列表项） */
export interface ChatSession {
  id: string              // 会话唯一 ID
  name: string            // 显示名称（好友备注/群备注优先，否则昵称或群名）
  lastMessage: string     // 列表预览的最后一条消息摘要
  time: string            // 最后消息时间 HH:mm
  avatarText: string      // 无头像 URL 时的文字头像
  avatarColor: string     // 文字头像背景色
  unread?: number         // 未读消息数
  muted?: boolean         // 是否免打扰
  pinned?: boolean        // 是否置顶
  blocked?: boolean       // 是否拉黑（单聊）
  /** 群聊有未读的 @我 / @全体成员（进入会话后清除） */
  atMe?: boolean
  isGroup?: boolean       // 是否为群聊
  /** 群真实名称（群聊；与 name 区分，name 可能是备注） */
  groupName?: string
  /** 当前用户对本群备注 */
  groupRemark?: string
  /** 好友在线状态（单聊） */
  online?: boolean
  avatarUrl?: string      // 远程头像 URL（单聊或自定义群头像）
  /** 群成员头像拼图（群聊默认头像） */
  memberAvatars?: Array<{ text: string; color?: string; imageUrl?: string }>
  /** 单聊对方用户 ID */
  peerUserId?: string
  /** 是否为后端真实会话（非 Mock） */
  isReal?: boolean
  /** 消息页虚拟会话：站内日程提醒 */
  isSystemNotify?: boolean
}

/** 消息页「日程提醒」虚拟会话 ID */
export const SYSTEM_NOTIFY_SESSION_ID = '__calendar_remind__'

/** 单条聊天消息 */
export interface ChatMessage {
  id: string              // 消息 ID
  sessionId: string       // 所属会话 ID
  content: string         // 文本内容或占位描述
  time: string            // 发送时间 HH:mm
  isSelf: boolean         // 是否为自己发送
  senderId?: string       // 群聊发送者用户 ID
  senderName?: string     // 群聊发送者昵称
  senderAvatar?: string   // 群聊发送者头像
  // 消息类型：文本/图片/文件/链接/系统/语音/红包/数据卡片/撤回
  type?: 'text' | 'image' | 'file' | 'link' | 'system' | 'voice' | 'redPacket' | 'dataCard' | 'recall' | 'time'
  linkUrl?: string        // 链接消息 URL
  /** 服务端创建时间（毫秒），用于撤回时限等 */
  createTime?: number

  // file 类型扩展字段
  fileName?: string
  fileSize?: string
  fileUrl?: string
  /** 文件卡片底栏：已发送 / 已下载 */
  fileStatus?: string

  // image 类型扩展
  isImage?: boolean

  /** 语音消息时长（秒） */
  voiceDuration?: number
  voiceUrl?: string

  /** 红包相关 */
  redPacketGreeting?: string
  redPacketAmount?: string
  redPacketOpened?: boolean
  /** 后端红包 ID（来自 MessageItem.fileUrl） */
  redPacketId?: string
  /** 红包类型：normal 普通 / lucky 拼手气 */
  redPacketType?: 'normal' | 'lucky'
  /** 红包总个数 */
  redPacketTotalCount?: number
  /** 红包剩余个数 */
  redPacketRemainingCount?: number
  /** 当前用户是否已领取 */
  redPacketReceived?: boolean
  /** 已领取金额（仅当前用户视角） */
  redPacketReceivedAmount?: string
  /** 红包状态：active / finished / expired */
  redPacketStatus?: 'active' | 'finished' | 'expired'

  /** 数据卡片（知流等） */
  dataCardTitle?: string
  dataCardSub?: string
  dataCardLabel?: string
  dataCardValue?: string

  // 引用回复的原消息
  replyTo?: ChatMessage
}

/** 通讯录联系人项 */
export interface ContactItem {
  id: string
  name: string
  avatarText: string
  avatarColor: string
  group: string           // 分组名（如「我的好友」）
  online?: boolean
  avatarUrl?: string      // 头像图片 URL
  /** 后端用户 ID，存在时可拉取真实公开资料 */
  userId?: string
}

/** 收藏项 */
export interface FavoriteItem {
  id: string
  title: string
  preview: string
  time: string
  type: 'note' | 'image' | 'link' | 'file'
}

// 聊天背景预设 ID
export type ChatBackgroundId = 'default' | 'purple' | 'orange'

/** Sidebar 导航项配置（静态或动态 badge） */
export interface SidebarItem {
  key: string
  icon: string
  badge?: number          // 未读角标数字
}
