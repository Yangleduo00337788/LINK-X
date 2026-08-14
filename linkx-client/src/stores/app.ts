/**
 * 作者：yangleduo
 */
/**
 * 应用核心 Store（app）
 * 全局导航、聊天会话/消息、主题、登录态、锁屏与用户资料等核心业务状态
 */

// Pinia Store 工厂
import { defineStore } from 'pinia'
// 核心业务类型
import type { NavKey, ChatSession, ChatMessage, ContactItem } from '../types'
import * as chatApi from '../api/chat'
import * as friendApi from '../api/friend'
import {
  connectChatSocket,
  disconnectChatSocket,
  sendChatMessage,
  sendDeliveryReceipt,
  isChatSocketConnected,
  ensureChatSocketConnected,
  resetChatSocketReconnect
} from '../utils/chatSocket'
import {
  conversationToSession,
  messageToChatMessage,
  messagePreviewFromItem
} from '../utils/chatMapper'
import {
  filePreviewLabel,
  imagePreviewPlaceholder,
  locationPreviewLabel,
  meetingPreviewLabel,
  recalledPreviewLabel,
  redPacketPreviewLabel,
  videoCallPreviewLabel,
  voiceCallPreviewLabel,
  voicePreviewLabel
} from '../utils/messagePreviewText'
import { compareMessageOrder } from '../utils/messageOrder'
import { enrichMessageReplyQuotes, preserveReplyTo } from '../utils/enrichMessageReply'
import { isMessageIdAtOrBefore } from '../utils/messageStatus'
import {
  contentMentionsUser,
  notifyFriendOnline,
  notifyIncomingMessage,
  shouldAlertForSession
} from '../utils/messageNotify'
import { useAppSettingsStore } from './appSettings'
import { dataUrlToFile } from '../utils/fileConvert'
import { generateUuidV4 } from '../utils/parseJson'
import type { MessageItem } from '../types/chat'
import { resolveUserAvatarUrl } from '../utils/defaultAvatar'
import { isLinkMateBotSender } from '../utils/linkmateLogo'
import { normalizeMediaUrl } from '../utils/mediaUrl'

let reportSessionReadTimer: ReturnType<typeof setTimeout> | null = null
const sessionMessagesLoadTasks = new Map<string, Promise<void>>()

function findLastReadableMessageId(messages: ChatMessage[]): string {
  let lastId = ''
  for (const m of messages) {
    if (!m?.id || m.type === 'time' || m.type === 'system') continue
    if (!/^\d+$/.test(m.id)) continue
    if (!lastId || BigInt(m.id) > BigInt(lastId)) {
      lastId = m.id
    }
  }
  return lastId
}
import { API_BASE_URL } from '../config/endpoints'
import { t } from '../i18n'
import { normalizeProfileGender, PROFILE_GENDER_MALE, type ProfileGender } from '../types/profileGender'
// 通讯录 Store（加群/加好友后同步联系人）
import { useContactsStore } from './contacts'
// 群元数据 Store（邀请成员等）
import { useGroupMetaStore } from './groupMeta'
// 主题同步到 document 与 Electron 主进程
import { applyDocumentTheme, notifyElectronTheme } from '../utils/themeSync'
// 持久化前清理敏感或过大字段
import { sanitizeAppPersistState } from '../utils/persistSanitize'
import { useAppSettingsStore } from './appSettings'
// 登出时重置其它 UI Store
import { resetSessionUi, resetSessionStores, cleanupNaiveUiOverlays } from '../utils/resetSessionUi'
import { useNotificationsStore } from './notifications'
// HTTP 客户端与认证 API
import * as authApi from '../api/auth'
import * as userApi from '../api/user'
import * as groupApi from '../api/group'
import type { UpdateProfileRequest } from '../api/user'
import { clearTokens, getRefreshToken, hasRefreshToken, isWebEnvironment, saveTokenPair } from '../utils/tokenStorage'
import { hasLockPin as isLockPinConfigured, verifyLockPin as verifyLockPinHash, saveLockPinHash } from '../utils/lockPin'
import type { UserInfo } from '../types/auth'
import type { UserProfileData } from '../api/user'
import { validateLockPin } from '../utils/validation'

/** sendMessage 可选参数：扩展消息类型与附件字段 */
export interface SendMessageOptions {
  type?: ChatMessage['type']   // 消息类型，默认 text
  replyTo?: ChatMessage        // 引用回复的消息
  fileName?: string              // 文件名
  fileSize?: string              // 文件大小
  fileUrl?: string               // 文件 URL
  isImage?: boolean              // 是否按图片展示
  voiceDuration?: number         // 语音时长（秒）
  voiceUrl?: string                // 语音 URL
  redPacketGreeting?: string       // 红包祝福语
  redPacketAmount?: string         // 红包金额
  rawFile?: File                   // 原始文件（上传用）
  /** 自动重试已用次数（失败退避时保留，避免无限重试） */
  autoRetryCount?: number
}

/** 记住账号 / 自动登录（不存储密码；头像与昵称供登录页展示） */
export interface SavedLogin {
  username: string
  rememberMe: boolean
  autoLogin: boolean
  avatar?: string
  nickname?: string
}

/** 创建群聊时传入的成员摘要 */
export interface CreateGroupMember {
  id: string
  name: string
  avatarText: string
  avatarColor: string
  avatarUrl?: string
}

/** 群头像可选背景色池 */
const GROUP_COLORS = ['#12b7f5', '#52c41a', '#722ed1', '#fa8c16', '#eb2f96', '#13c2c2']

function preserveMessageSendMeta(next: ChatMessage, prev: ChatMessage | null | undefined) {
  if (!prev) return
  if (prev.readCount != null) next.readCount = prev.readCount
  if (prev.totalMembers != null) next.totalMembers = prev.totalMembers
  next.uploadProgress = undefined
  if (prev.type === 'file' && next.sendStatus === 'sent') {
    next.fileStatus = undefined
  }
}

function applyGroupReadMetaToMessage(
  chatMsg: ChatMessage,
  sessionId: string,
  session: ChatSession | undefined
) {
  if (!session?.isGroup) return
  if (chatMsg.totalMembers != null && chatMsg.totalMembers > 0) {
    if (chatMsg.readCount == null) chatMsg.readCount = 0
    return
  }
  const memberCount = useGroupMetaStore().membersFor(sessionId).length
  if (memberCount > 0) {
    chatMsg.totalMembers = memberCount
    chatMsg.readCount = chatMsg.readCount ?? 0
  }
}

/**
 * 根据消息类型生成会话列表「最后一条消息」预览文案
 * @param msg 聊天消息
 */
function messagePreview(msg: ChatMessage): string {
  if (msg.type === 'file') return filePreviewLabel(msg.fileName || msg.content)
  if (msg.type === 'image' || msg.isImage) return imagePreviewPlaceholder()
  if (msg.type === 'voice') return voicePreviewLabel()
  if (msg.type === 'location') return locationPreviewLabel(msg.content || '')
  if (msg.type === 'redPacket') {
    return redPacketPreviewLabel(msg.redPacketGreeting)
  }
  if (msg.type === 'conference') {
    const content = (msg.content || '').trim()
    if (content && (/语音通话|视频通话|会议|Voice call|Video call|Meeting/i.test(content))) {
      return content
    }
    const scene = msg.conferenceScene
    const kind =
      scene === 'call'
        ? msg.conferenceType === 'voice'
          ? voiceCallPreviewLabel()
          : videoCallPreviewLabel()
        : /语音通话|Voice call/i.test(content)
          ? voiceCallPreviewLabel()
          : /视频通话|Video call/i.test(content)
            ? videoCallPreviewLabel()
            : meetingPreviewLabel()
    return `[${kind}] ${msg.conferenceTitle || msg.fileName || kind}`
  }
  if (msg.type === 'recall') return recalledPreviewLabel()
  return msg.content
}

/** 返回当前本地时间的 HH:mm 字符串 */
function nowTime(): string {
  const now = new Date()
  return `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`
}

/**
 * 根据字符串 seed 确定性选取群头像颜色
 * @param seed 通常为群名称
 */
function pickGroupColor(seed: string): string {
  let hash = 0
  for (let i = 0; i < seed.length; i++) hash += seed.charCodeAt(i) // 简单字符码累加
  return GROUP_COLORS[hash % GROUP_COLORS.length]
}

/**
 * 规范化用户 ID：仅接受数字（含字符串形式的雪花 ID），其它情况返回空串，
 * 避免把异常值（如含特殊字符的字段）拼到 WebSocket/URL 中。
 */
function sanitizeUserId(raw: unknown): string {
  if (raw == null) return ''
  const s = String(raw).trim()
  return /^\d{1,32}$/.test(s) ? s : ''
}

/** 判断是否更像网络不可达（而非账号/token 业务错误） */
function isLikelyNetworkError(error: unknown): boolean {
  const err = error as {
    code?: string
    message?: string
    response?: unknown
  }
  if (err?.response) return false
  const code = err?.code || ''
  if (code === 'ERR_NETWORK' || code === 'ECONNABORTED' || code === 'ETIMEDOUT') return true
  const msg = (err?.message || '').toLowerCase()
  return (
    msg.includes('network') ||
    msg.includes('timeout') ||
    msg.includes('failed to fetch') ||
    msg.includes('networkerror')
  )
}

type ProfileSource = Partial<UserProfileData & UserInfo>

/**
 * 解析生日为毫秒时间戳。
 * 后端 Jackson 全局把 Long 序列化成字符串（防雪花 ID 精度丢失），
 * 因此 birthday 常为 "946656000000"；Date.parse 对纯数字字符串会得到 NaN。
 */
function parseBirthday(value: unknown): number | null {
  if (value == null || value === '') return null
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null
  }
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (!trimmed) return null
    if (/^-?\d+$/.test(trimmed)) {
      const n = Number(trimmed)
      return Number.isFinite(n) ? n : null
    }
    const parsed = Date.parse(trimmed)
    return Number.isNaN(parsed) ? null : parsed
  }
  return null
}

/** 将后端用户资料写入 store 的 userProfile 结构 */
function mapApiProfile(data: ProfileSource) {
  const gender = normalizeProfileGender(data.gender)
  const birthday = parseBirthday(data.birthday)
  return {
    nickname: data.nickname || data.username || '',
    username: data.username || '',
    signature: data.signature?.trim() ? data.signature : '',
    avatar: resolveUserAvatarUrl(data.avatar, sanitizeUserId(data.id)) || '',
    userId: sanitizeUserId(data.id),
    gender,
    birthday,
    country: data.country || t('modals.china'),
    province: data.province || '',
    region: data.region || '',
    email: data.email || null,
    emailBound: !!data.emailBound,
    phone: data.phone || null,
    phoneBound: !!data.phoneBound
  }
}

// [P3-10] 收集消息自动重试定时器，登出/重置时统一清理，避免内存泄漏与登出后触发重试
const pendingRetryTimers = new Set<ReturnType<typeof setTimeout>>()
const groupReadCountRefreshTimers: Record<string, ReturnType<typeof setTimeout>> = {}

// 定义并导出 app Store
export const useAppStore = defineStore('app', {
  // 应用全局初始状态
  state: () => ({
    navKey: 'chat' as NavKey,                                    // 当前主导航模块
    sessions: [] as ChatSession[],                                // 会话列表（从后端加载）
    messagesBySession: {} as Record<string, ChatMessage[]>,       // 各会话消息映射（从后端加载）
    currentSessionId: null as string | null,                     // 当前打开的会话 id
    /** 每次点击进入会话递增，用于强制滚到最新（含重复点击同一会话） */
    sessionEnterTick: 0,
    /** 打开会话后待滚动定位并高亮的消息 ID（搜索跳转等） */
    pendingFocusMessageId: null as string | null,
    theme: 'light' as 'light' | 'dark',                          // 明暗主题
    contactsActiveView: 'none' as 'none' | 'friend-notifs' | 'group-notifs', // 通讯录子视图
    userProfile: {
      nickname: '',
      username: '',
      signature: '',
      avatar: '',
      userId: '',
      gender: PROFILE_GENDER_MALE as ProfileGender,
      birthday: null as number | null,
      country: t('modals.china'),
      province: '',
      region: '',
      email: null as string | null,
      emailBound: false,
      phone: null as string | null,
      phoneBound: false
    },
    isLoggedIn: false,   // 是否已登录
    isLoading: false,    // 登录等异步操作加载中
    authInitializing: false, // 仅在 Refresh Token 自动登录期间为 true
    isOffline: false,    // 离线模式（WebSocket 断开时为 true）
    isLocked: false,     // 是否处于锁屏状态
    chatInitialized: false, // 是否已加载真实会话
    messagesLoaded: {} as Record<string, boolean>, // 各会话历史是否已拉取
    messagesHasMore: {} as Record<string, boolean>, // 各会话是否还有更早消息
    messagesLoading: {} as Record<string, boolean>, // 各会话是否正在加载历史
    /** 各会话输入草稿（打开会话时从服务端拉取） */
    draftBySession: {} as Record<string, string>,
    /** 会话内对方正在输入（userId + 过期时间） */
    typingBySession: {} as Record<string, { userId: string; name?: string; until: number }>,
    savedLogin: {
      username: '',
      rememberMe: true,
      autoLogin: false,
      avatar: '',
      nickname: ''
    } as SavedLogin
  }),

  getters: {
    /** 当前选中的会话对象 */
    currentSession(state): ChatSession | null {
      return state.sessions.find(s => s.id === state.currentSessionId) ?? null
    },
    /** 当前会话的消息数组 */
    currentMessages(state): ChatMessage[] {
      const id = state.currentSessionId
      if (!id) return []
      return state.messagesBySession[id] ?? []
    },
    /** 会话列表：重要 > 置顶 > 其余 */
    sortedSessions(state): ChatSession[] {
      return [...state.sessions].sort((a, b) => {
        if (!!a.important !== !!b.important) return a.important ? -1 : 1
        if (!!a.pinned !== !!b.pinned) return a.pinned ? -1 : 1
        return 0
      })
    },
    /** 仅群聊会话 */
    groupSessions(state): ChatSession[] {
      return state.sessions.filter(s => s.isGroup)
    }
  },

  actions: {
    /**
     * 切换主导航；离开通讯录时重置通讯录子视图
     * @param key 导航键
     */
    setNav(key: NavKey) {
      this.navKey = key
      if (key !== 'contacts') {
        this.contactsActiveView = 'none'
      }
    },

    /** 重置通讯录内子面板为默认 */
    resetContactsView() {
      this.contactsActiveView = 'none'
    },

    /**
     * 选中某会话：清未读、确保消息数组存在
     * @param session 要选中的会话
     */
    selectSession(session: ChatSession) {
      this.contactsActiveView = 'none'
      this.currentSessionId = session.id
      this.sessionEnterTick++
      const s = this.sessions.find(x => x.id === session.id)
      const hadAtMe = !!(s?.atMe || s?.atMeMessageId)
      if (s) {
        this.$patch((state) => {
          const idx = state.sessions.findIndex(x => x.id === session.id)
          if (idx < 0) return
          state.sessions[idx].unread = 0
          state.sessions[idx].atMeNeedAck = hadAtMe
          state.sessions[idx].atMe = hadAtMe
        })
      }
      if (!this.messagesBySession[session.id]) {
        this.messagesBySession[session.id] = []
      }

      // 有未读 @：进会话后保留提示，需点浮层确认，避免贴底后「只剩普通消息」
      if (hadAtMe && s) {
        const needRecover = !s.atMeMessageId
        if (session.isReal) {
          void this.loadSessionMessages(session.id).then(() => {
            if (needRecover) this.recoverAtMeMessageId(session.id)
            void this.reportSessionRead(session.id, { immediate: true })
          })
        } else if (needRecover) {
          this.recoverAtMeMessageId(session.id)
        }
      } else if (session.isReal) {
        void this.loadSessionMessages(session.id).then(() => {
          void this.reportSessionRead(session.id, { immediate: true })
        })
      }
      if (session.isReal) {
        void this.loadSessionDraft(session.id)
      }
    },

    /** 向服务端同步已读游标（清本端未读）；是否向对方推送 readReceipt 由服务端按隐私设置决定 */
    async reportSessionRead(
      sessionId: string,
      options?: { immediate?: boolean; lastMessageId?: string }
    ) {
      if (!sessionId) return

      const flush = async () => {
        const msgs = this.messagesBySession[sessionId] || []
        const lastId = options?.lastMessageId || findLastReadableMessageId(msgs)
        if (!lastId) return
        try {
          const res = await chatApi.markAsRead(sessionId, lastId)
          if (res.code === 200) {
            const session = this.sessions.find(s => s.id === sessionId)
            if (session) session.unread = 0
          }
        } catch (e) {
          console.warn('上报已读失败:', e)
        }
      }

      if (options?.immediate) {
        if (reportSessionReadTimer) {
          clearTimeout(reportSessionReadTimer)
          reportSessionReadTimer = null
        }
        await flush()
        return
      }

      if (reportSessionReadTimer) clearTimeout(reportSessionReadTimer)
      reportSessionReadTimer = setTimeout(() => {
        reportSessionReadTimer = null
        void flush()
      }, 280)
    },

    /** 退出/切后台前立即上报当前会话已读，避免 debounce 未落库导致重启后红点复现 */
    async flushReportSessionRead() {
      if (reportSessionReadTimer) {
        clearTimeout(reportSessionReadTimer)
        reportSessionReadTimer = null
      }
      const sessionId = this.currentSessionId
      if (!sessionId) return
      await this.reportSessionRead(sessionId, { immediate: true })
    },

    /**
     * 历史拉取后补发送达确认，避免「在线时未收、刷新后才看到」时对方仍停在「已发送」。
     * 仅对非本人、雪花 ID 消息回执；单次最多 40 条防止刷屏。
     */
    ackHistoryDeliveries(sessionId: string, messages: ChatMessage[]) {
      if (!sessionId || !messages?.length) return
      const me = this.userProfile.userId
      let sent = 0
      for (let i = messages.length - 1; i >= 0 && sent < 40; i--) {
        const m = messages[i]
        if (!m?.id || !/^\d+$/.test(m.id)) continue
        if (m.type === 'system' || m.type === 'time' || m.type === 'recall') continue
        if (m.isSelf || (me && m.senderId === me)) continue
        if (m.deliveryStatus === 'delivered' || m.deliveryStatus === 'read') continue
        sendDeliveryReceipt(m.id)
        sent += 1
      }
    },

    /** 清除会话的 @我 提示（点击浮层跳转后调用） */
    clearAtMeMessage(sessionId: string) {
      const s = this.sessions.find(x => x.id === sessionId)
      if (!s) return
      s.atMe = false
      s.atMeMessageId = undefined
      s.atMeNeedAck = false
    },

    /** 从已加载消息中找回最近一条 @我，写入 atMeMessageId */
    recoverAtMeMessageId(sessionId: string) {
      const s = this.sessions.find(x => x.id === sessionId)
      if (!s?.isGroup || s.atMeMessageId) return
      const names = [this.userProfile.nickname, this.userProfile.username]
      const me = this.userProfile.userId
      const list = this.messagesBySession[sessionId] || []
      for (let i = list.length - 1; i >= 0; i--) {
        const m = list[i]
        if (m.isSelf || (me && m.senderId === me)) continue
        if (m.type === 'system' || m.type === 'time' || m.type === 'recall') continue
        if (contentMentionsUser(m.content, names)) {
          s.atMeMessageId = m.id
          s.atMe = true
          return
        }
      }
    },

    /**
     * 确保会话存在于列表中；已存在则选中，否则插入并选中
     * @param session 目标会话
     * @returns 最终使用的会话对象
     */
    ensureSession(session: ChatSession) {
      // 优先按 id 匹配
      const existingById = this.sessions.find(s => s.id === session.id)
      if (existingById) {
        this.selectSession(existingById)
        this.navKey = 'chat'
        return existingById
      }

      // 后端返回的真实会话（isReal: true）：替换本地同名旧会话，避免残留缓存导致的 ID 不一致
      if (session.isReal && !session.isGroup) {
        const idx = this.sessions.findIndex(s => !s.isGroup && s.name === session.name && s.id !== session.id)
        if (idx >= 0) {
          this.sessions.splice(idx, 1)
        }
      }

      // 单聊：按名称匹配（避免重复会话）
      const existingByName =
        !session.isGroup
          ? this.sessions.find(s => !s.isGroup && s.name === session.name)
          : undefined
      if (existingByName) {
        this.selectSession(existingByName)
        this.navKey = 'chat'
        return existingByName
      }

      this.sessions.unshift(session) // 新会话插到列表顶部
      this.selectSession(session)
      this.navKey = 'chat'
      return session
    },

    /**
     * 从联系人发起单聊
     * @param contact 联系人
     */
    async startChatWithContact(contact: ContactItem) {
      const friendUserId = contact.userId || contact.id
      await this.openPrivateChat(friendUserId, contact.name, contact.avatarUrl)
    },

    /**
     * 创建群聊并写入系统欢迎消息
     * @param members 初始成员（不含自己）
     * @param groupName 可选群名
     * @returns 新群会话，无成员时返回 null
     */
    async createGroup(members: CreateGroupMember[], groupName?: string) {
      if (members.length === 0) return null

      const name =
        groupName?.trim() ||
        (members.length <= 2
          ? members.map(m => m.name).join('、')
          : t('chat.groupWithCount', { n: members.length + 1 }))

      try {
        const res = await groupApi.createGroup({
          name,
          memberIds: members.map(m => m.id)
        })

        if (res.code === 200 && res.data) {
          const groupConv = res.data
          const memberAvatars = (groupConv.memberAvatars || []).slice(0, 9).map(m => {
            const nick = m.nickname || '?'
            return {
              text: nick.charAt(0) || '?',
              color: pickGroupColor(nick),
              imageUrl: normalizeMediaUrl(m.avatar) || undefined
            }
          })
          // 创建时后端可能尚未返回拼图数据，用本地已选成员兜底
          const faces =
            memberAvatars.length > 0
              ? memberAvatars
              : [
                  {
                    text: this.userProfile.nickname?.charAt(0) || t('defaults.me'),
                    color: pickGroupColor(this.userProfile.nickname || 'me'),
                    imageUrl: this.userProfile.avatar || undefined
                  },
                  ...members.slice(0, 8).map(m => ({
                    text: m.name.charAt(0) || '?',
                    color: pickGroupColor(m.name),
                    imageUrl: m.avatarUrl
                  }))
                ]
          const session: ChatSession = {
            id: String(groupConv.id),
            name: groupConv.name || name,
            groupName: groupConv.name || name,
            lastMessage: t('chat.systemWelcomeJoin'),
            time: nowTime(),
            avatarText: (groupConv.name || name).charAt(0) || t('defaults.groupChar'),
            avatarColor: pickGroupColor(groupConv.name || name),
            avatarUrl: normalizeMediaUrl(groupConv.avatar) || undefined,
            memberAvatars: faces,
            isGroup: true,
            isReal: true
          }
          this.messagesBySession[session.id] = [
            {
              id: `msg-sys-${Date.now()}`,
              sessionId: session.id,
              content: t('chat.systemGroupCreated', { name: this.userProfile.nickname }),
              time: nowTime(),
              isSelf: false,
              type: 'system'
            }
          ]
          this.ensureSession(session)
          return session
        }
        throw new Error(res.message || t('modals.createFail'))
      } catch (e) {
        console.error('创建群聊失败:', e)
        throw e
      }
    },

    /**
     * 打开已有群聊会话（从后端会话列表中定位）
     * @param conversationId 群会话 ID
     */
    async openGroupSession(conversationId: string) {
      let session = this.sessions.find(s => s.id === conversationId && s.isGroup)
      if (session) {
        this.selectSession(session)
        this.navKey = 'chat'
        return session
      }

      await this.loadChatSessions()
      session = this.sessions.find(s => s.id === conversationId && s.isGroup)
      if (session) {
        this.selectSession(session)
        this.navKey = 'chat'
        return session
      }

      throw new Error(t('errors.groupNotFound'))
    },

    async addFriendSession(friend: { userId: string; name: string; avatarUrl?: string }) {
      return this.openPrivateChat(friend.userId, friend.name, friend.avatarUrl)
    },

    /** 登录后拉取好友、通知与聊天会话，并连接 WebSocket */
    async loadSocialData() {
      this.isOffline = false
      // 立刻建连：会话列表可能来自本地缓存，用户可马上操作；不能等偏好/好友接口结束才连 WS
      void this.connectChatWebSocket()

      try {
        // 标记进入登录态；拉取并合并服务端偏好
        const { useAppSettingsStore } = await import('./appSettings')
        useAppSettingsStore().markOnline()
        await useAppSettingsStore().loadFromServer()

        // 把服务端的 autoStart 同步到 Electron 主进程（如果当前不一致就纠正）
        try {
          const settings = useAppSettingsStore()
          const want = settings.autoStart
          const current = await window.electronAPI?.getAutoStart?.()
          if (typeof current === 'boolean' && current !== want) {
            await window.electronAPI?.setAutoStart?.(want)
          }
        } catch (e) {
          console.warn('[app] 同步开机自启失败:', e)
        }

        await Promise.all([
          useContactsStore().fetchFriends(),
          useNotificationsStore().fetchFriendRequests(),
          useNotificationsStore().fetchGroupInvitations(),
          useNotificationsStore().fetchMessageNotifications()
        ])
        void import('./calendar').then(({ useCalendarStore }) => {
          void useCalendarStore().ensureReminderWatch()
        })
        // 确保登录后不自动选中任何会话
        this.currentSessionId = null
        await this.loadChatSessions()
        // 刷新后若仍在会中，提示重新加入
        const uid = String(this.userProfile.userId || '')
        if (uid) {
          void import('./conference').then(({ useConferenceStore }) => {
            void useConferenceStore().tryRestoreActive(uid)
          })
        }
      } catch (e) {
        console.error('[app] 加载社交数据失败:', e)
      } finally {
        // 再确保一次（首连若因时序失败，这里补连）
        void this.connectChatWebSocket()
      }
    },

    /** 从后端拉取真实单聊会话列表 */
    async loadChatSessions() {
      try {
        const res = await chatApi.listSessions()
        if (res.code !== 200 || !res.data) return

        const prevById = new Map(this.sessions.map(s => [s.id, s]))
        const realSessions = res.data.map(conversationToSession)
        // 保留本地 @我 提示；置顶/免打扰/未读优先用服务端字段
        this.sessions = realSessions.map(s => {
          const prev = prevById.get(s.id)
          if (!prev) return s
          const wasOnline = !!prev.online
          const nowOnline = !!s.online
          let lastSeenAt = s.lastSeenAt ?? prev.lastSeenAt
          if (wasOnline && !nowOnline && !lastSeenAt) {
            lastSeenAt = Date.now()
          }
          return {
            ...s,
            lastSeenAt,
            unread:
              s.unread != null
                ? s.unread
                : prev.unread != null
                  ? prev.unread
                  : undefined,
            atMe: prev.atMe,
            atMeMessageId: prev.atMeMessageId,
            atMeNeedAck: prev.atMeNeedAck,
            muted: s.muted ?? prev.muted,
            pinned: s.pinned ?? prev.pinned,
            important: s.important ?? prev.important,
            blocked: s.blocked ?? prev.blocked
          }
        })
        this.chatInitialized = true
      } catch (e) {
        console.error('加载会话列表失败:', e)
      }
    },

    /**
     * 从后端拉取群聊会话列表（{@code GET /group/list}），并合并到本地会话列表。
     */
    async loadGroups() {
      try {
        const res = await groupApi.listGroups()
        if (res.code !== 200 || !res.data) return []

        const groupSessions = res.data.map(conversationToSession)
        for (const session of groupSessions) {
          if (!this.sessions.some(s => s.id === session.id)) {
            this.sessions.push(session)
          }
        }
        return groupSessions
      } catch (e) {
        console.error('加载群聊列表失败:', e)
        return []
      }
    },

    /** 拉取指定会话的历史消息（首屏） */
    async loadSessionMessages(sessionId: string) {
      if (this.messagesLoaded[sessionId]) return
      const pending = sessionMessagesLoadTasks.get(sessionId)
      if (pending) {
        await pending
        return
      }

      const task = (async () => {
        if (this.messagesLoaded[sessionId]) return
        this.messagesLoading[sessionId] = true
        try {
          const res = await chatApi.listMessages(sessionId)
          if (res.code === 200 && res.data) {
            const serverMessages = res.data.map(m => messageToChatMessage(m, sessionId))

            // 获取本地已有的消息（可能是用户刚发的乐观消息，还没收到 ack）
            const localMessages = this.messagesBySession[sessionId] || []

            // 合并逻辑：
            // 1. 用 Set 记录服务端消息 ID（都是数字格式）
            // 2. 保留本地不在服务端列表中的消息（乐观消息的 ID 是 UUID 格式）
            // 3. 合并后按时间排序
            const serverIds = new Set(serverMessages.map(m => m.id))
            const localOnlyMessages = localMessages.filter(m => !serverIds.has(m.id))

            // 合并：服务端消息 + 本地乐观消息；按雪花 id 升序（与后端游标一致）
            const merged = [...serverMessages, ...localOnlyMessages]
            merged.sort(compareMessageOrder)
            enrichMessageReplyQuotes(merged)

            const session = this.sessions.find(s => s.id === sessionId)
            if (session?.isGroup) {
              const members = useGroupMetaStore().membersFor(sessionId)
              if (members.length) {
                this.enrichGroupSelfMessageReadMeta(sessionId, members.length)
              }
            }

            console.log('[loadSessionMessages]', {
              sessionId,
              serverCount: serverMessages.length,
              localCount: localMessages.length,
              localOnlyCount: localOnlyMessages.length,
              mergedCount: merged.length,
              currentSessionId: this.currentSessionId
            })

            this.messagesBySession[sessionId] = merged
            this.messagesLoaded[sessionId] = true
            this.messagesHasMore[sessionId] = res.data.length >= 50
            this.ackHistoryDeliveries(sessionId, serverMessages)

            // 历史里若有会议邀请，同步聊天顶栏进行中状态
            for (let i = merged.length - 1; i >= 0; i--) {
              const m = merged[i]
              if (m.type === 'conference' && (m.conferenceId || m.fileUrl)) {
                void import('./conference').then(({ useConferenceStore }) => {
                  useConferenceStore().noteConferenceInviteMessage({
                    conversationId: sessionId,
                    conferenceId: String(m.conferenceId || m.fileUrl),
                    title: m.conferenceTitle || m.fileName,
                    type: m.conferenceType,
                    scene: m.conferenceScene,
                    hasPassword: !!m.conferenceHasPassword
                  })
                })
                break
              }
            }
          }
        } catch (e) {
          console.error('加载历史消息失败:', e)
        } finally {
          this.messagesLoading[sessionId] = false
          sessionMessagesLoadTasks.delete(sessionId)
        }
      })()

      sessionMessagesLoadTasks.set(sessionId, task)
      await task
    },

    /** 加载更早的历史消息（向上翻页） */
    async loadMoreMessages(sessionId: string) {
      if (!this.messagesLoaded[sessionId]) return
      if (!this.messagesHasMore[sessionId] || this.messagesLoading[sessionId]) return

      const existing = this.messagesBySession[sessionId]
      if (!existing?.length) return

      const oldestId = existing[0].id
      if (oldestId.startsWith('temp-')) return

      this.messagesLoading[sessionId] = true
      try {
        const res = await chatApi.listMessages(sessionId, oldestId)
        if (res.code === 200 && res.data?.length) {
          const older = res.data.map(m => messageToChatMessage(m, sessionId))
          const existingIds = new Set(existing.map(m => m.id))
          const unique = older.filter(m => !existingIds.has(m.id))
          if (unique.length) {
            const combined = [...unique, ...existing]
            enrichMessageReplyQuotes(combined)
            this.messagesBySession[sessionId] = combined
            this.ackHistoryDeliveries(sessionId, unique)
          }
          this.messagesHasMore[sessionId] = res.data.length >= 50
        } else {
          this.messagesHasMore[sessionId] = false
        }
      } catch (e) {
        console.error('加载更多消息失败:', e)
      } finally {
        this.messagesLoading[sessionId] = false
      }
    },

    /** 打开或创建与好友的单聊会话 */
    async openPrivateChat(friendUserId: string, name: string, avatarUrl?: string) {
      const existing = this.sessions.find(s => s.isReal && s.peerUserId === friendUserId)
      if (existing) {
        this.selectSession(existing)
        this.navKey = 'chat'
        return existing
      }

      try {
        const res = await chatApi.openPrivateChat(friendUserId)
        if (res.code === 200 && res.data) {
          const session = conversationToSession(res.data)
          if (!session.avatarUrl && avatarUrl) {
            session.avatarUrl = avatarUrl
          }
          if (session.name === t('defaults.friend') && name) {
            session.name = name
            session.avatarText = name.charAt(0) || '?'
          }
          this.ensureSession(session)
          await this.loadSessionMessages(session.id)
          return session
        }
        throw new Error(res.message || t('contacts.openSessionFail'))
      } catch (e) {
        console.error('打开单聊失败:', e)
        throw e
      }
    },

    /** 连接 IM WebSocket（返回 Promise，便于发送前等待就绪） */
    async connectChatWebSocket() {
      resetChatSocketReconnect()
      const handlers = {
        onOpen: () => {
          this.isOffline = false
        },
        onClose: () => {
          if (this.isLoggedIn) {
            this.isOffline = true
          }
        },
        onError: (code: number, msg: string, clientMsgId?: string) => {
          if (clientMsgId) {
            this.handleWsSendError(clientMsgId, code, msg)
            return
          }
          if (code !== 401) {
            console.warn('WebSocket 错误:', msg)
          }
        },
        onMessage: (message: import('../types/chat').MessageItem) => {
          this.handleIncomingWsMessage(message)
        },
        onAck: (clientMsgId: string, message: import('../types/chat').MessageItem) => {
          this.handleWsAck(clientMsgId, message)
        },
        onRecall: (message: import('../types/chat').MessageItem) => {
          this.applyRecalledMessage(message)
        },
        onCallEvent: (action: string, data: Record<string, unknown>) => {
          void import('./call').then(({ useCallStore }) => {
            useCallStore().handleRemoteEvent(
              action,
              data as unknown as import('../api/call').CallEventPayload
            )
          })
          // 会议 mesh 信令也走 call_signal，按 callId 分流到 conference store
          if (action === 'call_signal') {
            void import('./conference').then(({ useConferenceStore }) => {
              useConferenceStore().handleCallSignal(
                data as unknown as import('../api/call').CallEventPayload
              )
            })
          }
        },
        onCustomAction: (action: string, data: Record<string, unknown>) => {
          if (action === 'presence') {
            this.applyPresenceUpdate(data)
            return
          }
          if (action === 'notification_refresh') {
            void import('./notifications').then(({ useNotificationsStore }) => {
              void useNotificationsStore().refreshFromSocket(data)
            })
            return
          }
          if (action === 'deliveryReceipt') {
            this.handleDeliveryReceipt(data)
            return
          }
          if (action === 'readReceipt') {
            this.handleReadReceipt(data)
            return
          }
          if (action === 'typing') {
            this.handleTypingIndicator(data)
            return
          }
          if (action === 'edit') {
            this.applyEditedMessage(data as unknown as MessageItem)
            return
          }
          // 直接被拉入群聊：收到 group_added 后直接将群加入会话列表
          if (action === 'group_added') {
            void this.handleGroupAdded(data)
            return
          }
          // 群名 / 公告变更推送
          if (action === 'group_renamed' || action === 'group_announcement_updated') {
            void this.handleGroupUpdate(data)
            return
          }
          if (action === 'group_dissolved') {
            this.handleGroupDissolved(data)
            return
          }
          if (action === 'sensitive_alert_clear') {
            const messageId = String(data.messageId || '')
            const conversationId = data.conversationId ? String(data.conversationId) : undefined
            if (messageId) this.clearSensitiveAlert(messageId, conversationId)
            return
          }
          // 群角色变更推送
          if (action === 'group_member_role_changed') {
            void this.handleGroupMemberRoleChanged(data)
            return
          }
          // 群禁言状态变更推送
          if (action === 'group_mute_changed' || action === 'group_mute_all_changed') {
            void this.handleGroupMuteChanged(data)
            return
          }
          if (action === 'group_linkmate_changed') {
            void this.handleGroupLinkmateChanged(data)
            return
          }
          // 朋友圈新动态推送
          if (action === 'moments_new_post') {
            void this.handleMomentsNewPost(data)
            return
          }
          // 群成员加入推送
          if (action === 'group_member_added') {
            void this.handleGroupMemberAdded(data)
            return
          }
          if (
            action === 'conference_invite' ||
            action === 'conference_end' ||
            action === 'conference_remove' ||
            action === 'conference_join' ||
            action === 'conference_leave' ||
            action === 'conference_mute' ||
            action === 'conference_video' ||
            action === 'conference_host' ||
            action === 'conference_admit' ||
            action === 'conference_waiting' ||
            action === 'conference_raise' ||
            action === 'conference_role' ||
            action === 'conference_presence'
          ) {
            void import('./conference').then(({ useConferenceStore }) => {
              useConferenceStore().handleRemoteEvent(action, data)
            })
          }
        }
      }
      await connectChatSocket(handlers)
    },

    /** 断开 IM WebSocket */
    disconnectChatWebSocket() {
      disconnectChatSocket()
      this.isOffline = false
    },

    /** IM @灵伴：插入流式占位消息 */
    ensureStreamingLinkMateMessage(sessionId: string, tempId: string) {
      if (!this.messagesBySession[sessionId]) {
        this.messagesBySession[sessionId] = []
      }
      const list = this.messagesBySession[sessionId]
      if (list.some(m => m.id === tempId)) return
      const now = new Date()
      list.push({
        id: tempId,
        sessionId,
        content: '',
        time: `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`,
        isSelf: false,
        senderId: '0',
        senderName: t('linkmate.atName'),
        type: 'text',
        streaming: true
      })
    },

    /** IM @灵伴：更新流式内容 */
    updateStreamingLinkMateMessage(sessionId: string, tempId: string, content: string) {
      const msg = this.messagesBySession[sessionId]?.find(m => m.id === tempId)
      if (msg) msg.content = content
    },

    /** IM @灵伴：更新流式推理内容 */
    updateStreamingLinkMateReasoning(
      sessionId: string,
      tempId: string,
      reasoningContent: string
    ) {
      const msg = this.messagesBySession[sessionId]?.find(m => m.id === tempId)
      if (msg) msg.reasoningContent = reasoningContent
    },

    /** IM @灵伴：流式完成，替换为正式消息 */
    finalizeStreamingLinkMateMessage(
      sessionId: string,
      tempId: string,
      messageId: string,
      content: string
    ) {
      const list = this.messagesBySession[sessionId]
      if (!list) return
      const now = new Date()
      const finalized: ChatMessage = {
        id: messageId,
        sessionId,
        content,
        time: `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`,
        isSelf: false,
        senderId: '0',
        senderName: t('linkmate.atName'),
        type: 'text',
        streaming: false
      }
      const withoutPlaceholders = list.filter(
        m =>
          m.id !== tempId &&
          m.id !== messageId &&
          !m.id.startsWith('temp-linkmate-') &&
          !(m.streaming && isLinkMateBotSender(m.senderId))
      )
      withoutPlaceholders.push(finalized)
      this.messagesBySession[sessionId] = withoutPlaceholders
      if (this.currentSessionId === sessionId && /^\d+$/.test(messageId)) {
        void this.reportSessionRead(sessionId, { lastMessageId: messageId, immediate: true })
      }
    },

    /** IM @灵伴：移除流式占位 */
    removeStreamingLinkMateMessage(sessionId: string, tempId: string) {
      const list = this.messagesBySession[sessionId]
      if (!list) return
      this.messagesBySession[sessionId] = list.filter(m => m.id !== tempId)
    },

    /** 处理 WebSocket 推送的新消息 */
    handleIncomingWsMessage(message: MessageItem) {
      // 后端 MessageVO.conversationId 是 Long 类型，JSON 反序列化后是数字
      // 而前端会话 id 是字符串，需要统一为字符串进行比较和查找
      const sessionId = String(message.conversationId)
      const chatMsg = messageToChatMessage(message, sessionId)
      const me = this.userProfile.userId
      const fromSelf =
        !!message.isSelf || (!!me && String(message.senderId) === String(me))

      // 接收端确认送达，驱动发送方「已送达」
      if (!fromSelf && message.type !== 'system' && chatMsg.id && /^\d+$/.test(chatMsg.id)) {
        sendDeliveryReceipt(chatMsg.id)
      }

      if (!this.messagesBySession[sessionId]) {
        this.messagesBySession[sessionId] = []
      }

      if (isLinkMateBotSender(message.senderId)) {
        this.messagesBySession[sessionId] = this.messagesBySession[sessionId].filter(
          m =>
            !m.id.startsWith('temp-linkmate-') &&
            !(m.streaming && isLinkMateBotSender(m.senderId))
        )
      }

      const exists = this.messagesBySession[sessionId].some(m => m.id === chatMsg.id)
      if (!exists) {
        if (chatMsg.replyTo) {
          enrichMessageReplyQuotes([chatMsg, ...this.messagesBySession[sessionId]])
        }
        this.messagesBySession[sessionId].push(chatMsg)
      } else if (
        isLinkMateBotSender(message.senderId) &&
        this.currentSessionId === sessionId &&
        /^\d+$/.test(chatMsg.id)
      ) {
        // 流式占位已 finalize 时 WS 会重复推送，仍需推进已读游标
        void this.reportSessionRead(sessionId, { lastMessageId: chatMsg.id, immediate: true })
      }

      if (chatMsg.type === 'conference' && (chatMsg.conferenceId || chatMsg.fileUrl)) {
        void import('./conference').then(({ useConferenceStore }) => {
          useConferenceStore().noteConferenceInviteMessage({
            conversationId: sessionId,
            conferenceId: String(chatMsg.conferenceId || chatMsg.fileUrl),
            title: chatMsg.conferenceTitle || chatMsg.fileName,
            type: chatMsg.conferenceType,
            scene: chatMsg.conferenceScene,
            hasPassword: !!chatMsg.conferenceHasPassword
          })
        })
      }

      // 使用转换后的字符串 ID 查找会话
      const session = this.sessions.find(s => s.id === sessionId)
      if (session) {
        session.lastMessage = messagePreviewFromItem(message)
        session.time = chatMsg.time
        // 系统提示不计未读、不响铃
        if (!exists && message.type !== 'system') {
          const mentioned =
            !!session.isGroup &&
            !fromSelf &&
            contentMentionsUser(message.content, [
              this.userProfile.nickname,
              this.userProfile.username
            ])
          if (mentioned) {
            session.atMeMessageId = chatMsg.id
            session.atMe = true
            // 当前会话但未贴底：需要点浮层；贴底时直接看见则不强制 ack
            if (this.currentSessionId === sessionId) {
              session.atMeNeedAck = true
            }
          }

          if (this.currentSessionId !== sessionId) {
            const settings = useAppSettingsStore()
            if (
              shouldAlertForSession(session, message, {
                notifyAtMe: settings.notifyAtMe,
                myNickname: this.userProfile.nickname,
                myUsername: this.userProfile.username
              })
            ) {
              session.unread = (session.unread || 0) + 1
            }
          } else if (!fromSelf) {
            const lastMessageId = /^\d+$/.test(chatMsg.id) ? chatMsg.id : undefined
            void this.reportSessionRead(sessionId, {
              lastMessageId,
              immediate: isLinkMateBotSender(message.senderId)
            })
          }
        }
      } else {
        // 会话不在本地列表中，尝试重新加载会话列表
        void this.loadChatSessions()
      }

      // 新消息才提醒（声音 / 桌面通知，受消息通知偏好控制）；系统提示静默
      if (!exists && message.type !== 'system') {
        notifyIncomingMessage({
          message,
          session,
          sessionId,
          currentSessionId: this.currentSessionId,
          myNickname: this.userProfile.nickname,
          myUsername: this.userProfile.username
        })
      }
    },

    /** 处理 WebSocket 发送确认，替换乐观消息 */
    handleWsAck(clientMsgId: string, message: MessageItem) {
      let sessionId = message.conversationId != null ? String(message.conversationId) : ''

      // 确保消息列表存在（如果没有，先初始化）
      if (sessionId && !this.messagesBySession[sessionId]) {
        this.messagesBySession[sessionId] = []
      }

      let index = sessionId
        ? this.messagesBySession[sessionId].findIndex(m => m.id === clientMsgId || m.clientMsgId === clientMsgId)
        : -1

      // conversationId 异常时按 clientMsgId 回查，避免乐观消息一直卡在「发送中」
      if (index < 0) {
        for (const sid of Object.keys(this.messagesBySession)) {
          const list = this.messagesBySession[sid]
          const i = list?.findIndex(m => m.id === clientMsgId || m.clientMsgId === clientMsgId) ?? -1
          if (i >= 0) {
            sessionId = sid
            index = i
            break
          }
        }
      }

      if (!sessionId) return

      if (!this.messagesBySession[sessionId]) {
        this.messagesBySession[sessionId] = []
      }

      const chatMsg = messageToChatMessage(
        { ...message, conversationId: message.conversationId ?? sessionId, isSelf: true },
        sessionId
      )
      chatMsg.sendStatus = 'sent'
      chatMsg.clientMsgId = clientMsgId
      if (message.sensitiveAlert) {
        chatMsg.sensitiveAlert = true
      }
      const prev = index >= 0 ? this.messagesBySession[sessionId][index] : null
      const session = this.sessions.find(s => s.id === sessionId)
      preserveReplyTo(chatMsg, prev)
      preserveMessageSendMeta(chatMsg, prev)
      applyGroupReadMetaToMessage(chatMsg, sessionId, session)
      if (chatMsg.replyTo) enrichMessageReplyQuotes([chatMsg, ...this.messagesBySession[sessionId]])

      if (index >= 0) {
        this.messagesBySession[sessionId].splice(index, 1, chatMsg)
      } else {
        const exists = this.messagesBySession[sessionId].some(m => m.id === chatMsg.id)
        if (!exists) {
          this.messagesBySession[sessionId].push(chatMsg)
        }
      }

      if (session) {
        session.lastMessage = messagePreviewFromItem(message)
        session.time = chatMsg.time
      }
      if (session?.isGroup) {
        this.scheduleLatestSelfGroupReadCountRefresh(sessionId)
      }
    },

    /**
     * WebSocket 业务错误（如敏感词拦截）：
     * - 敏感词拦截：移除乐观消息，恢复会话预览，避免列表显示错误文案
     * - 其它业务错误：标记失败且不自动重试
     */
    handleWsSendError(clientMsgId: string, code: number, msg: string) {
      const reason = msg || t('chat.sensitiveBlocked')
      const isSensitiveBlock = /违禁|敏感词|无法发送/.test(reason)
      let marked = false
      for (const sessionId of Object.keys(this.messagesBySession)) {
        const list = this.messagesBySession[sessionId]
        if (!list?.length) continue
        const index = list.findIndex(m => m.id === clientMsgId || m.clientMsgId === clientMsgId)
        if (index < 0) continue
        if (isSensitiveBlock) {
          list.splice(index, 1)
          const session = this.sessions.find(s => s.id === sessionId)
          if (session) {
            const last = [...list].reverse().find(m => m.sendStatus !== 'failed')
            session.lastMessage = last ? messagePreview(last) : ''
            session.time = last?.time || session.time
          }
        } else {
          const local = list[index]
          local.sendStatus = 'failed'
          local.uploadProgress = undefined
          local.sendFailReason = reason
          if (local.type === 'file') local.fileStatus = t('chat.fileStatusFailed')
          ;(local as { _autoRetry?: number })._autoRetry = 99
        }
        marked = true
        break
      }
      if (code === 400 || marked) {
        console.warn('[handleWsSendError]', { clientMsgId, code, msg: reason })
      } else if (code !== 401) {
        console.warn('WebSocket 错误:', msg)
      }
    },

    /** 管理端驳回/处理后，清除本地敏感词告警提示 */
    clearSensitiveAlert(messageId: string, conversationId?: string) {
      const id = String(messageId)
      const sessionIds = conversationId
        ? [String(conversationId)]
        : Object.keys(this.messagesBySession)
      for (const sessionId of sessionIds) {
        const list = this.messagesBySession[sessionId]
        if (!list?.length) continue
        const local = list.find(m => m.id === id || m.clientMsgId === id)
        if (!local) continue
        local.sensitiveAlert = false
        break
      }
    },

    /** 群被解散：从会话列表移除 */
    handleGroupDissolved(data: Record<string, unknown>) {
      const conversationId = String(data.conversationId || '')
      if (!conversationId) return
      this.sessions = this.sessions.filter(s => s.id !== conversationId)
      delete this.messagesBySession[conversationId]
      delete this.messagesLoaded[conversationId]
      delete this.messagesHasMore[conversationId]
      if (this.currentSessionId === conversationId) {
        this.currentSessionId = this.sessions[0]?.id ?? null
      }
    },

    /** 重置聊天相关状态（登出时） */
    resetChatState() {
      // [P3-10] 清理未执行的消息重试定时器，避免登出后仍触发重试
      for (const timer of pendingRetryTimers) {
        clearTimeout(timer)
      }
      pendingRetryTimers.clear()
      this.disconnectChatWebSocket()
      this.sessions = this.sessions.filter(s => !s.isReal)
      for (const id of Object.keys(this.messagesBySession)) {
        const session = this.sessions.find(s => s.id === id)
        if (!session) delete this.messagesBySession[id]
      }
      this.messagesLoaded = {}
      this.messagesHasMore = {}
      this.messagesLoading = {}
      this.chatInitialized = false
      if (!this.sessions.some(s => s.id === this.currentSessionId)) {
        this.currentSessionId = this.sessions[0]?.id ?? null
      }
    },

    /** 切换会话置顶状态（调用后端，失败回滚） */
    async toggleSessionPin(sessionId: string) {
      const s = this.sessions.find(x => x.id === sessionId)
      if (!s) return
      const prev = !!s.pinned
      s.pinned = !prev
      try {
        const res = await chatApi.togglePin(sessionId)
        if (res.code !== 200) {
          s.pinned = prev
          throw new Error(res.message || t('errors.pinFailed'))
        }
      } catch (e) {
        s.pinned = prev
        console.error('切换置顶失败:', e)
        throw e
      }
    },

    /** 切换重要会话高亮（调用后端，失败回滚） */
    async toggleSessionImportant(sessionId: string) {
      const s = this.sessions.find(x => x.id === sessionId)
      if (!s) return
      const prev = !!s.important
      s.important = !prev
      try {
        const res = await chatApi.toggleImportant(sessionId)
        if (res.code !== 200) {
          s.important = prev
          throw new Error(res.message || t('errors.markImportantFailed'))
        }
      } catch (e) {
        s.important = prev
        console.error('切换重要会话失败:', e)
        throw e
      }
    },

    /**
     * 打开会话并定位到指定消息（搜索/收藏跳转）。
     * ChatPanel 监听 pendingFocusMessageId 后滚动并高亮。
     */
    openSessionAtMessage(sessionId: string, messageId: string) {
      const session = this.sessions.find(s => s.id === sessionId)
      if (!session) return false
      this.pendingFocusMessageId = messageId
      this.navKey = 'chat'
      this.selectSession(session)
      return true
    },

    clearPendingFocusMessage() {
      this.pendingFocusMessageId = null
    },

    /** 切换会话免打扰（调用后端，失败回滚） */
    async toggleSessionMute(sessionId: string) {
      const s = this.sessions.find(x => x.id === sessionId)
      if (!s) return
      const prev = !!s.muted
      s.muted = !prev
      try {
        const res = await chatApi.toggleMute(sessionId)
        if (res.code !== 200) {
          s.muted = prev
          throw new Error(res.message || t('errors.muteSessionFailed'))
        }
      } catch (e) {
        s.muted = prev
        console.error('切换免打扰失败:', e)
        throw e
      }
    },

    /** 加载会话草稿，写入 draftBySession 供输入框读取 */
    async loadSessionDraft(sessionId: string): Promise<string> {
      try {
        const res = await chatApi.getDraft(sessionId)
        const content = res.code === 200 && typeof res.data === 'string' ? res.data : ''
        this.draftBySession[sessionId] = content
        return content
      } catch (e) {
        console.warn('加载草稿失败:', e)
        return this.draftBySession[sessionId] || ''
      }
    },

    async saveSessionDraft(sessionId: string, content: string) {
      this.draftBySession[sessionId] = content
      try {
        await chatApi.saveDraft(sessionId, content)
      } catch (e) {
        console.warn('保存草稿失败:', e)
      }
    },

    async clearSessionDraft(sessionId: string) {
      this.draftBySession[sessionId] = ''
      try {
        await chatApi.saveDraft(sessionId, '')
      } catch (e) {
        console.warn('清除草稿失败:', e)
      }
    },

    /** 好友上下线推送：更新通讯录绿点与单聊会话 online；好友上线时提示 */
    applyPresenceUpdate(data: Record<string, unknown>) {
      const userId = sanitizeUserId(data.userId)
      if (!userId) return
      const online = data.online === true || data.online === 'true' || data.online === 1
      const contacts = useContactsStore()
      const changed = contacts.setOnline(userId, online)
      const now = Date.now()
      for (const session of this.sessions) {
        if (!session.isGroup && session.peerUserId && String(session.peerUserId) === userId) {
          session.online = online
          if (!online) session.lastSeenAt = now
        }
      }
      if (!online) {
        contacts.setLastSeen(userId, now)
      }
      if (changed && online) {
        const friend = contacts.items.find(c => String(c.userId ?? c.id) === userId)
        notifyFriendOnline(friend?.name || userId, friend?.avatarUrl)
      }
    },

    handleDeliveryReceipt(data: Record<string, unknown>) {
      const sessionId = data.conversationId != null ? String(data.conversationId) : ''
      const messageId = data.messageId != null ? String(data.messageId) : ''
      if (!sessionId || !messageId) return
      const msgs = this.messagesBySession[sessionId]
      if (!msgs) return
      const target = msgs.find(m => m.id === messageId)
      if (!target) return
      target.deliveryStatus = String(data.deliveryStatus || 'delivered')
      target.sendStatus = 'delivered'
      if (target.type === 'file') target.fileStatus = undefined
    },

    handleReadReceipt(data: Record<string, unknown>) {
      const sessionId = data.conversationId != null ? String(data.conversationId) : ''
      const lastReadMessageId =
        data.lastReadMessageId != null ? String(data.lastReadMessageId) : ''
      if (!sessionId || !lastReadMessageId || lastReadMessageId === '0') return
      const readerId = data.readerId != null ? String(data.readerId) : ''
      const me = this.userProfile.userId ? String(this.userProfile.userId) : ''
      if (readerId && me && readerId === me) return

      const session = this.sessions.find(s => s.id === sessionId)
      if (session?.isGroup) {
        this.scheduleLatestSelfGroupReadCountRefresh(sessionId)
        return
      }

      const msgs = this.messagesBySession[sessionId]
      if (!msgs) return
      for (const m of msgs) {
        if (!m.isSelf) continue
        if (!isMessageIdAtOrBefore(m.id, lastReadMessageId)) continue
        if (
          m.sendStatus === 'sent' ||
          m.sendStatus === 'delivered' ||
          m.sendStatus === 'sending'
        ) {
          m.sendStatus = 'read'
          m.deliveryStatus = 'read'
          if (m.type === 'file') m.fileStatus = undefined
        }
      }
    },

    /** 群聊：仅刷新最新一条己方消息的已读人数（避免历史消息被游标误标全员已读） */
    scheduleLatestSelfGroupReadCountRefresh(sessionId: string) {
      const prev = groupReadCountRefreshTimers[sessionId]
      if (prev) clearTimeout(prev)
      groupReadCountRefreshTimers[sessionId] = setTimeout(() => {
        delete groupReadCountRefreshTimers[sessionId]
        void this.fetchLatestSelfGroupReadCount(sessionId)
      }, 300)
    },

    async fetchLatestSelfGroupReadCount(sessionId: string) {
      const msgs = this.messagesBySession[sessionId]
      if (!msgs?.length) return
      for (let i = msgs.length - 1; i >= 0; i--) {
        const m = msgs[i]
        if (!m.isSelf) continue
        if (m.type === 'time' || m.type === 'system' || m.type === 'recall') continue
        if (!/^\d+$/.test(m.id)) continue
        try {
          const res = await chatApi.getMessageReadCount(sessionId, m.id)
          if (res.code === 200 && res.data) {
            this.setMessageReadCount(
              sessionId,
              m.id,
              Number(res.data.readCount) || 0,
              Number(res.data.totalMembers) || 0
            )
          }
        } catch {
          /* ignore */
        }
        break
      }
    },

    setMessageReadCount(
      sessionId: string,
      messageId: string,
      readCount: number,
      totalMembers: number
    ) {
      const msgs = this.messagesBySession[sessionId]
      if (!msgs) return
      const target = msgs.find(m => m.id === messageId)
      if (!target) return
      target.readCount = readCount
      target.totalMembers = totalMembers
    },

    enrichGroupSelfMessageReadMeta(sessionId: string, totalMembers: number) {
      if (!totalMembers || totalMembers <= 0) return
      const msgs = this.messagesBySession[sessionId]
      if (!msgs) return
      for (const m of msgs) {
        if (m.isSelf) {
          m.totalMembers = totalMembers
          if (m.readCount == null) m.readCount = 0
        }
      }
    },

    handleTypingIndicator(data: Record<string, unknown>) {
      const sessionId = data.conversationId != null ? String(data.conversationId) : ''
      const userId = data.userId != null ? String(data.userId) : ''
      if (!sessionId || !userId) return
      const me = this.userProfile.userId ? String(this.userProfile.userId) : ''
      if (userId === me) return
      const session = this.sessions.find(s => s.id === sessionId)
      if (!session || session.isGroup) return
      const until = Date.now() + 4000
      let name: string | undefined
      if (session.peerUserId && String(session.peerUserId) === userId) {
        name = session.name
      }
      this.typingBySession[sessionId] = { userId, name, until }
      setTimeout(() => {
        const cur = this.typingBySession[sessionId]
        if (cur && cur.until <= Date.now()) {
          delete this.typingBySession[sessionId]
        }
      }, 4100)
    },

    applyEditedMessage(message: MessageItem) {
      const sessionId = String(message.conversationId)
      const msgs = this.messagesBySession[sessionId]
      if (!msgs) return
      const index = msgs.findIndex(m => m.id === String(message.id))
      if (index < 0) return
      const prev = msgs[index]
      const next = messageToChatMessage(message, sessionId)
      next.sendStatus = prev.sendStatus
      next.readCount = prev.readCount
      next.totalMembers = prev.totalMembers
      next.clientMsgId = prev.clientMsgId
      msgs.splice(index, 1, next)
      const session = this.sessions.find(s => s.id === sessionId)
      if (session && index === msgs.length - 1) {
        session.lastMessage = messagePreviewFromItem(message)
        session.time = next.time
      }
    },

    async editMessage(messageId: string, content: string) {
      const sessionId = this.currentSessionId
      if (!sessionId) return false
      const trimmed = content.trim()
      if (!trimmed) return false
      try {
        const res = await chatApi.editMessage(sessionId, messageId, trimmed)
        if (res.code !== 200 || !res.data) {
          throw new Error(res.message || t('chat.editFail'))
        }
        this.applyEditedMessage(res.data)
        return true
      } catch (e) {
        console.error('编辑消息失败:', e)
        throw e
      }
    },

    async forwardMessage(messageId: string, targetConversationId: string) {
      const sessionId = this.currentSessionId
      if (!sessionId || !targetConversationId) return false
      try {
        const res = await chatApi.forwardMessage(sessionId, messageId, targetConversationId)
        if (res.code !== 200 || !res.data) {
          throw new Error(res.message || t('chat.forwardFail'))
        }
        const targetId = String(res.data.conversationId || targetConversationId)
        const chatMsg = messageToChatMessage(res.data, targetId)
        chatMsg.sendStatus = 'sent'
        if (!this.messagesBySession[targetId]) {
          this.messagesBySession[targetId] = []
        }
        if (!this.messagesBySession[targetId].some(m => m.id === chatMsg.id)) {
          this.messagesBySession[targetId].push(chatMsg)
        }
        const session = this.sessions.find(s => s.id === targetId)
        if (session) {
          session.lastMessage = messagePreviewFromItem(res.data)
          session.time = chatMsg.time
        }
        return true
      } catch (e) {
        console.error('转发消息失败:', e)
        throw e
      }
    },

    async retryFailedMessage(messageId: string) {
      const sessionId = this.currentSessionId
      if (!sessionId) return
      const msgs = this.messagesBySession[sessionId]
      if (!msgs) return
      const target = msgs.find(m => m.id === messageId)
      if (!target || !target.isSelf || target.sendStatus !== 'failed') return

      const content = target.content
      const type = target.type || 'text'
      const replyTo = target.replyTo
      const autoRetryCount = Number((target as { _autoRetry?: number })._autoRetry || 0)
      const idx = msgs.findIndex(m => m.id === messageId)
      if (idx >= 0) msgs.splice(idx, 1)

      try {
        await this.sendMessageReal(content, {
          type: type === 'image' ? 'image' : type === 'file' ? 'file' : type === 'voice' ? 'voice' : 'text',
          replyTo,
          fileName: target.fileName,
          fileSize: target.fileSize,
          fileUrl: target.fileUrl,
          isImage: target.isImage,
          voiceDuration: target.voiceDuration,
          voiceUrl: target.voiceUrl,
          autoRetryCount
        })
      } catch (e) {
        console.error('重试发送失败:', e)
        throw e
      }
    },

    /**
     * 删除会话及其全部消息；若删的是当前会话则选中列表第一项
     * @param sessionId 会话 id
     */
    deleteSession(sessionId: string) {
      this.sessions = this.sessions.filter(s => s.id !== sessionId)
      delete this.messagesBySession[sessionId]
      delete this.messagesLoaded[sessionId]
      if (this.currentSessionId === sessionId) {
        this.currentSessionId = this.sessions[0]?.id ?? null
      }
    },

    /** 按对方用户 ID 移除单聊会话（删除好友后使用） */
    removePrivateSessionByPeer(peerUserId: string) {
      const peer = String(peerUserId)
      const toRemove = this.sessions.filter(
        s => !s.isGroup && s.peerUserId && String(s.peerUserId) === peer
      )
      for (const s of toRemove) {
        this.deleteSession(s.id)
      }
    },

    /** 清空某会话消息记录并重置 lastMessage */
    clearSessionMessages(sessionId: string) {
      this.messagesBySession[sessionId] = []
      const session = this.sessions.find(s => s.id === sessionId)
      if (session) {
        session.lastMessage = ''
      }
    },

    /** 切换会话拉黑/屏蔽状态（云端同步） */
    async toggleSessionBlock(sessionId: string) {
      const s = this.sessions.find(x => x.id === sessionId)
      if (!s || s.isGroup) return
      const peerId = s.peerUserId
      if (!peerId) return
      const prev = !!s.blocked
      s.blocked = !prev
      try {
        const res = prev
          ? await friendApi.unblockFriend(peerId)
          : await friendApi.blockFriend(peerId)
        if (res.code !== 200) {
          s.blocked = prev
          throw new Error(res.message || t('errors.operationFailed'))
        }
      } catch (e) {
        s.blocked = prev
        throw e
      }
    },

    /**
     * 邀请成员入群：调用真实后端 {@code POST /group/{id}/members}。
     * 成功后刷新本地群成员缓存。
     * @param sessionId 群会话 id
     * @param memberIds 被邀请人 userId 列表
     * @returns 是否成功
     */
    async addGroupMembers(sessionId: string, memberIds: string[]): Promise<boolean> {
      const filtered = memberIds.filter(Boolean)
      if (!sessionId || filtered.length === 0) return false
      try {
        const res = await groupApi.addGroupMembers(sessionId, { memberIds: filtered })
        if (res.code === 200) {
          // 刷新本地群成员缓存，让侧栏/抽屉显示最新成员
          await useGroupMetaStore().fetchMembers(sessionId, true)
          return true
        }
        throw new Error(res.message || t('errors.inviteMemberFailed'))
      } catch (e) {
        console.error('邀请成员失败:', e)
        throw e
      }
    },

    /**
     * 退出群聊：调用真实后端 {@code POST /group/{id}/quit}。
     * 仅成功后从本地会话列表移除该群会话（失败不删，避免群主被误踢出列表）。
     */
    async leaveGroup(sessionId: string): Promise<void> {
      if (!sessionId) return
      try {
        const res = await groupApi.quitGroup(sessionId)
        if (res.code !== 200) {
          throw new Error(res.message || t('errors.quitGroupFailed'))
        }
        this.deleteSession(sessionId)
        useGroupMetaStore().clearForSession(sessionId)
      } catch (e) {
        console.error('退出群聊失败:', e)
        throw e
      }
    },

    /**
     * 转让群主：调用真实后端 {@code POST /group/{id}/transfer?newOwnerId=...}。
     */
    async transferGroupOwner(sessionId: string, newOwnerId: string): Promise<void> {
      const res = await groupApi.transferGroupOwner(sessionId, newOwnerId)
      if (res.code !== 200) {
        throw new Error(res.message || t('modals.transferOwnerFail'))
      }
      await useGroupMetaStore().fetchMembers(sessionId, true)
    },

    /**
     * 设置或取消管理员：{@code PUT /group/{id}/members/{memberId}/role}（仅群主）。
     */
    async updateMemberRole(
      sessionId: string,
      memberId: string,
      role: 'admin' | 'member'
    ): Promise<void> {
      const res = await groupApi.updateMemberRole(sessionId, memberId, role)
      if (res.code !== 200) {
        throw new Error(res.message || t('errors.updateRoleFailed'))
      }
      await useGroupMetaStore().fetchMembers(sessionId, true)
    },

    /**
     * 解散群聊：调用真实后端 {@code DELETE /group/{id}}（仅群主）。
     */
    async dissolveGroup(sessionId: string): Promise<void> {
      const res = await groupApi.dissolveGroup(sessionId)
      if (res.code !== 200) {
        throw new Error(res.message || t('modals.dissolveFail'))
      }
      this.deleteSession(sessionId)
      useGroupMetaStore().clearForSession(sessionId)
    },

    /**
     * 移除群成员：调用真实后端 {@code DELETE /group/{id}/members/{memberId}}（owner/admin）。
     */
    async removeGroupMember(sessionId: string, memberId: string): Promise<void> {
      const res = await groupApi.removeGroupMember(sessionId, memberId)
      if (res.code !== 200) {
        throw new Error(res.message || t('modals.removeMemberFail'))
      }
      await useGroupMetaStore().fetchMembers(sessionId, true)
    },

    /**
     * 向当前会话发送消息
     */
    async sendMessage(content: string, options: SendMessageOptions = {}) {
      const id = this.currentSessionId
      if (!id) return

      const session = this.sessions.find(s => s.id === id)
      if (session?.blocked) return

      if (!session) {
        console.warn('未找到当前会话')
        return
      }

      await this.sendMessageReal(content, options)
    },

    /** 通过 WebSocket 发送消息 */
    async sendMessageReal(content: string, options: SendMessageOptions = {}) {
      const id = this.currentSessionId
      if (!id) return

      const session = this.sessions.find(s => s.id === id)
      if (session?.blocked) return

      const type = options.type ?? 'text'
      const trimmed = content.trim()

      if (type === 'text' && !trimmed) return
      if (type === 'location' && !trimmed) return
      if (type === 'image' && !trimmed && !options.rawFile && !options.fileUrl) return
      if (type === 'voice' && !options.voiceDuration) return
      if (type === 'redPacket' && !options.redPacketAmount) return
      if (type === 'file' && !options.rawFile && !options.fileUrl) return

      const clientMsgId = generateUuidV4()
      const time = nowTime()
      const isImage = options.isImage ?? type === 'image'

      const optimistic: ChatMessage = {
        id: clientMsgId,
        sessionId: id,
        content:
          type === 'file'
            ? (options.fileName || trimmed || t('defaults.file'))
            : type === 'voice'
              ? t('chat.voiceMessageTag')
              : type === 'redPacket'
                ? (options.redPacketGreeting || trimmed || t('extra.greetingFallback'))
                : (trimmed || content),
        time,
        createTime: Date.now(),
        isSelf: true,
        type,
        replyTo: options.replyTo,
        fileName: options.fileName,
        fileSize: options.fileSize,
        fileUrl: options.fileUrl,
        isImage,
        fileStatus: type === 'file' ? t('chat.fileStatusSending') : undefined,
        voiceDuration: options.voiceDuration,
        voiceUrl: options.voiceUrl,
        redPacketGreeting: options.redPacketGreeting,
        redPacketAmount: options.redPacketAmount,
        redPacketOpened: type === 'redPacket' ? false : undefined,
        sendStatus: 'sending',
        uploadProgress: type === 'image' || type === 'file' || type === 'voice' ? 0 : undefined,
        clientMsgId,
        ...(options.autoRetryCount != null ? { _autoRetry: options.autoRetryCount } : {})
      } as ChatMessage & { _autoRetry?: number }

      if (session?.isGroup) {
        const gm = useGroupMetaStore()
        let memberCount = gm.membersFor(id).length
        if (memberCount <= 0) {
          await gm.fetchMembers(id)
          memberCount = gm.membersFor(id).length
        }
        if (memberCount > 0) {
          optimistic.totalMembers = memberCount
          optimistic.readCount = 0
        }
      }

      if (!this.messagesBySession[id]) {
        this.messagesBySession[id] = []
      }
      this.messagesBySession[id].push(optimistic)

      if (session) {
        session.lastMessage = messagePreview(optimistic)
        session.time = time
      }

      const markFailed = () => {
        const local = this.messagesBySession[id]?.find(m => m.id === clientMsgId)
        if (local) {
          local.sendStatus = 'failed'
          local.uploadProgress = undefined
          if (local.type === 'file') local.fileStatus = t('chat.fileStatusFailed')
          const retries = Number((local as { _autoRetry?: number })._autoRetry || 0)
          if (retries < 2) {
            ;(local as { _autoRetry?: number })._autoRetry = retries + 1
            const delay = 1000 * Math.pow(2, retries)
            const timer = setTimeout(() => {
              pendingRetryTimers.delete(timer)
              void this.retryFailedMessage(clientMsgId).catch(() => {
                /* 手动重试入口仍可用 */
              })
            }, delay)
            pendingRetryTimers.add(timer)
          }
        }
      }

      try {
        // 文本引用回复：走 REST quote，本地推入结果
        if (type === 'text' && options.replyTo?.id) {
          const res = await chatApi.quoteMessage(id, options.replyTo.id, {
            conversationId: id,
            msgType: 'text',
            content: trimmed,
            clientMsgId
          })
          if (res.code !== 200 || !res.data) {
            throw new Error(res.message || t('chat.messageSendFail'))
          }
          const chatMsg = messageToChatMessage(res.data, id)
          chatMsg.sendStatus = 'sent'
          chatMsg.clientMsgId = clientMsgId
          const idx = this.messagesBySession[id]?.findIndex(m => m.id === clientMsgId) ?? -1
          const prev = idx >= 0 ? this.messagesBySession[id][idx] : null
          preserveReplyTo(chatMsg, prev, options.replyTo)
          preserveMessageSendMeta(chatMsg, prev)
          applyGroupReadMetaToMessage(chatMsg, id, session)
          if (chatMsg.replyTo) {
            enrichMessageReplyQuotes([chatMsg, ...(this.messagesBySession[id] || [])])
          }
          if (idx >= 0) {
            this.messagesBySession[id].splice(idx, 1, chatMsg)
          } else if (!this.messagesBySession[id].some(m => m.id === chatMsg.id)) {
            this.messagesBySession[id].push(chatMsg)
          }
          if (session) {
            session.lastMessage = messagePreviewFromItem(res.data)
            session.time = chatMsg.time
          }
          void this.clearSessionDraft(id)
          return
        }

        let fileUrl = options.fileUrl
        let fileName = options.fileName
        let fileSizeNum: number | undefined

        if (type === 'image' || type === 'file' || type === 'voice') {
          let uploadFile: File | null = options.rawFile ?? null
          if (import.meta.env.DEV) {
            console.log('[发送消息] 准备上传文件:', {
              type,
              hasRawFile: !!uploadFile,
              rawFileSize: uploadFile?.size,
              rawFileName: uploadFile?.name
            })
          }
          if (!uploadFile && type === 'image' && content.startsWith('data:')) {
            uploadFile = dataUrlToFile(content, 'image.png')
          }
          if (uploadFile) {
            if (import.meta.env.DEV) {
              console.log('[发送消息] 上传文件:', uploadFile.name, uploadFile.size, uploadFile.type)
            }
            const applyUploadProgress = (percent: number) => {
              const local = this.messagesBySession[id]?.find(m => m.id === clientMsgId)
              if (!local) return
              local.uploadProgress = percent
              if (local.type === 'file') {
                local.fileStatus =
                  percent >= 100
                    ? t('chat.fileStatusSending')
                    : t('chat.fileStatusUploading', { n: percent })
              }
            }
            const uploadRes = await chatApi.uploadChatFileSmart(id, uploadFile, applyUploadProgress)
            if (import.meta.env.DEV) {
              console.log('[发送消息] 上传完成:', {
                code: uploadRes.code,
                fileName: uploadRes.data?.fileName,
                fileSize: uploadRes.data?.fileSize
              })
            }
            if (uploadRes.code !== 200 || !uploadRes.data) {
              throw new Error(uploadRes.message || t('errors.uploadFailed'))
            }
            // 入库/发送用 object key；本地预览用预签名 url
            const objectKey = uploadRes.data.fileKey || uploadRes.data.url
            const displayUrl =
              normalizeMediaUrl(uploadRes.data.url) || uploadRes.data.url || objectKey
            fileUrl = objectKey
            fileName = uploadRes.data.fileName || uploadFile.name
            // fileSize 可能是 number 或 string，统一转为 number
            const sizeValue = uploadRes.data.fileSize
            fileSizeNum = typeof sizeValue === 'string' ? Number(sizeValue) || 0 : sizeValue
            if (type === 'voice') {
              const local = this.messagesBySession[id]?.find(m => m.id === clientMsgId)
              if (local) {
                local.voiceUrl = displayUrl
                local.fileUrl = displayUrl
                local.fileName = fileName
                local.uploadProgress = 100
              }
            } else if (type === 'image' || type === 'file') {
              const local = this.messagesBySession[id]?.find(m => m.id === clientMsgId)
              if (local) {
                local.fileUrl = displayUrl
                if (type === 'image') local.content = displayUrl
                local.fileName = fileName
                if ((fileSizeNum ?? 0) > 0) {
                  const { formatFileSize } = await import('../utils/chatTime')
                  local.fileSize = formatFileSize(fileSizeNum ?? 0)
                }
                local.uploadProgress = 100
                if (type === 'file') local.fileStatus = t('chat.fileStatusSending')
              }
            }
          }
        }

        if (type === 'image' && !options.rawFile && !content.startsWith('data:') && !fileUrl) {
          fileUrl = trimmed || content
        }

        if ((type === 'image' || type === 'file' || type === 'voice') && !fileUrl) {
          throw new Error(t('errors.uploadFailed'))
        }

        if (!isChatSocketConnected()) {
          await this.connectChatWebSocket()
        }
        await ensureChatSocketConnected()

        sendChatMessage({
          action: 'send',
          clientMsgId,
          conversationId: id,
          msgType:
            type === 'text'
              ? 'text'
              : type === 'location'
                ? 'location'
                : type === 'image'
                  ? 'image'
                  : type === 'voice'
                    ? 'voice'
                    : 'file',
          content: type === 'text' || type === 'location' ? trimmed : fileUrl,
          fileName,
          fileSize: fileSizeNum,
          fileUrl: type === 'location' ? undefined : fileUrl,
          voiceDuration: type === 'voice' ? options.voiceDuration : undefined,
          quoteMessageId: options.replyTo?.id
        })
        if (type === 'text' || type === 'location') {
          void this.clearSessionDraft(id)
        }
      } catch (e) {
        markFailed()
        console.error('发送消息失败:', e)
        throw e
      }
    },

    /**
     * 撤回当前会话中的某条消息（调用后端并本地替换为撤回提示）
     * @param messageId 消息 id
     * @returns 是否成功
     */
    async recallMessage(messageId: string) {
      const sessionId = this.currentSessionId
      if (!sessionId) return false

      const msgs = this.messagesBySession[sessionId]
      if (!msgs) return false
      const index = msgs.findIndex(m => m.id === messageId)
      if (index === -1) return false
      const target = msgs[index]
      if (!target.isSelf || target.type === 'recall' || target.type === 'system') return false

      try {
        const res = await chatApi.recallMessage(sessionId, messageId)
        if (res.code !== 200 || !res.data) {
          throw new Error(res.message || t('chat.recallFail'))
        }
        this.applyRecalledMessage(res.data)
        return true
      } catch (e) {
        console.error('撤回消息失败:', e)
        throw e
      }
    },

    /**
     * 将消息替换为撤回态（API 回包或 WS recall 推送）
     */
    applyRecalledMessage(message: MessageItem) {
      const sessionId = String(message.conversationId)
      const chatMsg = messageToChatMessage(
        { ...message, type: 'recall', content: '', fileName: '', fileUrl: '' },
        sessionId
      )
      chatMsg.content = t('chat.preview.recalled')
      chatMsg.type = 'recall'
      chatMsg.isImage = false
      chatMsg.fileUrl = undefined
      chatMsg.fileName = undefined
      chatMsg.fileSize = undefined

      const msgs = this.messagesBySession[sessionId]
      if (msgs) {
        const index = msgs.findIndex(m => m.id === chatMsg.id)
        if (index >= 0) {
          msgs.splice(index, 1, { ...msgs[index], ...chatMsg, time: msgs[index].time || chatMsg.time })
        } else if (this.messagesLoaded[sessionId]) {
          // 历史已加载但本地没有该条（极端）：追加，避免丢提示
          msgs.push(chatMsg)
        }
      }

      const session = this.sessions.find(s => s.id === sessionId)
      if (session) {
        const list = this.messagesBySession[sessionId]
        const last = list?.[list.length - 1]
        if (last) {
          session.lastMessage = messagePreview(last)
          session.time = last.time
        } else {
          session.lastMessage = messagePreview(chatMsg)
          if (chatMsg.time) session.time = chatMsg.time
        }
      }
    },

    /**
     * 标记红包消息为已领取
     * @param messageId 红包消息 id
     */
    openRedPacketMessage(messageId: string) {
      const sessionId = this.currentSessionId
      if (!sessionId) return false
      const msgs = this.messagesBySession[sessionId]
      const msg = msgs?.find(m => m.id === messageId)
      if (!msg || msg.type !== 'redPacket' || msg.redPacketOpened) return false
      msg.redPacketOpened = true
      return true
    },

    /** 切换明暗主题并同步到 DOM 与 Electron */
    toggleTheme() {
      this.theme = this.theme === 'light' ? 'dark' : 'light'
      applyDocumentTheme(this.theme)
      notifyElectronTheme(this.theme)
    },

    /** 更新昵称（本地+后端） */
    async updateNickname(name: string) {
      try {
        await this.updateProfile({ nickname: name })
      } catch {
        this.userProfile.nickname = name
      }
    },

    /** 将后端资料合并到本地 userProfile */
    applyUserProfile(data: ProfileSource) {
      this.userProfile = {
        ...this.userProfile,
        ...mapApiProfile(data)
      }
      // 同步到登录页缓存，登出后仍可展示上次头像/昵称
      if (this.savedLogin.rememberMe) {
        this.savedLogin.avatar = this.userProfile.avatar || ''
        this.savedLogin.nickname = this.userProfile.nickname || ''
      }
      if (data.username) {
        this.savedLogin.username = data.username
      }
    },

    /** 更新用户资料（昵称、签名、性别、生日、地区等） */
    async updateProfile(payload: UpdateProfileRequest) {
      try {
        const res = await userApi.updateProfile(payload)
        if (res.code === 200 && res.data) {
          this.applyUserProfile(res.data)
          return res.data
        }
        throw new Error(res.message || t('errors.updateFailed'))
      } catch (error) {
        // 网络失败时仍更新本地已提交的字段，避免用户感知丢失
        if (payload.nickname !== undefined) {
          this.userProfile.nickname = payload.nickname
          if (this.savedLogin.rememberMe) this.savedLogin.nickname = payload.nickname
        }
        if (payload.signature !== undefined) this.userProfile.signature = payload.signature
        if (payload.gender !== undefined) {
          this.userProfile.gender = normalizeProfileGender(payload.gender)
        }
        if (payload.birthday !== undefined) {
          this.userProfile.birthday = parseBirthday(payload.birthday)
        }
        if (payload.country !== undefined) this.userProfile.country = payload.country
        if (payload.province !== undefined) this.userProfile.province = payload.province
        if (payload.region !== undefined) this.userProfile.region = payload.region
        throw error
      }
    },

    /** 更新头像 */
    async updateAvatar(file: File) {
      const res = await userApi.uploadAvatar(file)
      if (res.code === 200 && res.data) {
        const uid = this.userProfile.userId
        const proxied =
          uid && /^\d+$/.test(String(uid))
            ? `${API_BASE_URL}/media/avatars/${uid}?v=${Date.now()}`
            : normalizeMediaUrl(res.data)
        this.userProfile.avatar = proxied || normalizeMediaUrl(res.data) || ''
        if (this.savedLogin.rememberMe) {
          this.savedLogin.avatar = this.userProfile.avatar
        }
        return this.userProfile.avatar
      }
      throw new Error(res.message || t('errors.uploadFailed'))
    },

    /** 获取当前用户信息 */
    async fetchCurrentUser() {
      try {
        const res = await userApi.getCurrentUser()
        if (res.code === 200 && res.data) {
          this.applyUserProfile(res.data)
          return res.data
        }
      } catch (e) {
        console.error('获取用户信息失败:', e)
      }
      return null
    },

    /**
     * 登出：先切回登录页并清理本地状态，再异步通知后端吊销 token
     */
    async logout() {
      const refresh = await getRefreshToken()

      // 先把偏好改动同步到服务端，避免 token 已吊销导致失败
      try {
        const { useAppSettingsStore } = await import('./appSettings')
        useAppSettingsStore().flushPendingSave()
        useAppSettingsStore().markOffline()
      } catch {
        /* 离线清理失败不影响登出主流程 */
      }

      resetSessionUi()
      await resetSessionStores()
      this.clearLocalChatCache()
      this.resetChatState()
      this.isLocked = false
      this.isLoggedIn = false
      // 记住账号时先把头像/昵称写入登录缓存，再清空运行时资料
      if (this.savedLogin.rememberMe) {
        this.savedLogin.avatar = this.userProfile.avatar || this.savedLogin.avatar || ''
        this.savedLogin.nickname = this.userProfile.nickname || this.savedLogin.nickname || ''
      } else {
        this.savedLogin.username = ''
        this.savedLogin.autoLogin = false
        this.savedLogin.avatar = ''
        this.savedLogin.nickname = ''
      }
      this.userProfile.nickname = ''
      this.userProfile.username = ''
      this.userProfile.signature = ''
      this.userProfile.avatar = ''
      this.userProfile.userId = ''
      this.userProfile.email = null
      this.userProfile.emailBound = false
      this.userProfile.phone = null
      this.userProfile.phoneBound = false
      cleanupNaiveUiOverlays()
      await clearTokens()

      try {
        await authApi.logout(refresh)
      } catch {
        // 本地已清理，服务端吊销失败可忽略
      }
    },

    /**
     * 调用后端登录接口
     * @param username 用户名
     * @param password 密码
     * @param opts 记住我 / 自动登录
     * @returns 是否登录成功
     */
    async login(username: string, password: string, opts?: { rememberMe?: boolean; autoLogin?: boolean; captchaId?: string; captchaCode?: string }) {
      const rememberMe = opts?.rememberMe ?? this.savedLogin.rememberMe
      const autoLogin = opts?.autoLogin ?? this.savedLogin.autoLogin

      this.isLoading = true
      try {
        const res = await authApi.login({
          username,
          password,
          captchaId: opts?.captchaId,
          captchaCode: opts?.captchaCode
        })
        if (res.code === 200 && res.data) {
          const { accessToken, refreshToken, user } = res.data
          await saveTokenPair(accessToken, refreshToken)

          this.savedLogin.rememberMe = rememberMe
          this.savedLogin.autoLogin = autoLogin
          this.savedLogin.username = rememberMe ? username : ''
          if (!rememberMe) {
            this.savedLogin.avatar = ''
            this.savedLogin.nickname = ''
          }
          this.applyUserProfile(user)

          this.isLoggedIn = true
          void this.fetchCurrentUser()
          void this.loadSocialData()
          return true
        }
        throw new Error(res.message || t('login.loginFail'))
      } finally {
        this.isLoading = false
      }
    },

    /**
     * 启动时用 Refresh Token 恢复会话。
     * 先判断离线；在线再刷 token。返回结果供登录页展示文案/提示。
     */
    async tryAutoLogin(): Promise<'ok' | 'offline' | 'failed' | 'skipped'> {
      if (this.isLoggedIn) return 'skipped'
      if (this.authInitializing) return 'skipped'
      if (!this.savedLogin.autoLogin || !this.savedLogin.rememberMe || !this.savedLogin.username) {
        return 'skipped'
      }

      // 先扫描是否离线，避免一直停在连接中
      if (typeof navigator !== 'undefined' && navigator.onLine === false) {
        this.savedLogin.autoLogin = false
        return 'offline'
      }

      this.authInitializing = true
      this.isLoading = true
      let outcome: 'ok' | 'offline' | 'failed' = 'failed'
      try {
        const isWeb = isWebEnvironment()
        // Web 环境 refresh token 在 HttpOnly Cookie 中（本地不可读），跳过本地校验直接走刷新接口；
        // Electron 环境必须从 safeStorage 读到 refresh token 才能刷新。
        let refresh: string | null = null
        if (!isWeb) {
          if (!(await hasRefreshToken())) {
            this.savedLogin.autoLogin = false
            return 'failed'
          }
          refresh = await getRefreshToken()
          if (!refresh) {
            this.savedLogin.autoLogin = false
            return 'failed'
          }
        }

        // Web 环境 refresh 为空字符串，后端从 HttpOnly Cookie 读取
        const res = await authApi.refreshToken(refresh ?? '')
        if (res.code === 200 && res.data) {
          await saveTokenPair(res.data.accessToken, res.data.refreshToken)
          this.applyUserProfile(res.data.user)
          this.isOffline = false
          outcome = 'ok'
          void this.fetchCurrentUser()
          void this.loadSocialData()
        } else {
          this.savedLogin.autoLogin = false
          outcome = 'failed'
        }
      } catch (error: unknown) {
        const offlineNow =
          (typeof navigator !== 'undefined' && navigator.onLine === false) || isLikelyNetworkError(error)
        if (offlineNow) {
          outcome = 'offline'
        } else {
          outcome = 'failed'
        }
        try {
          const { useAppSettingsStore } = await import('./appSettings')
          useAppSettingsStore().markOffline()
        } catch {
          /* ignore */
        }
        await clearTokens()
        this.isLoggedIn = false
        this.savedLogin.autoLogin = false
      } finally {
        if (outcome === 'ok') {
          this.isLoggedIn = true
        }
        this.isLoading = false
        this.authInitializing = false
      }
      return outcome
    },

    /** 设置锁屏 PIN（4-6 位数字，与登录密码独立） */
    async setLockPin(pin: string) {
      const err = validateLockPin(pin)
      if (err) throw new Error(err)
      await saveLockPinHash(pin)
    },

    /** 是否已设置锁屏 PIN */
    hasLockPin() {
      return isLockPinConfigured()
    },

    /** 锁屏 PIN 校验 */
    async verifyLockPin(pin: string): Promise<boolean> {
      if (!isLockPinConfigured()) {
        return false
      }
      return verifyLockPinHash(pin)
    },

/** 进入锁屏：清空本地聊天缓存，解锁后按会话重新拉取 */
    lock() {
      this.isLocked = true
      this.clearLocalChatCache()
    },

    /** 解除锁屏 */
    async unlock() {
      this.isLocked = false
      const sessionId = this.currentSessionId
      if (sessionId) {
        await this.loadSessionMessages(sessionId)
      }
    },

    /**
     * 清除 sessionStorage 中的聊天记录与内存缓存（锁屏/登出加固）。
     * 不删除服务端消息；解锁或切换会话时会重新拉取。
     */
    clearLocalChatCache() {
      this.messagesBySession = {}
      this.messagesLoaded = {}
      this.messagesLoading = {}
      try {
        sessionStorage.removeItem('linkx-app-msgs')
      } catch {
        /* ignore */
      }
    },

    /** 切换离线模式开关 */
    toggleOffline() {
      this.isOffline = !this.isOffline
    },

    /** 显式设置离线状态 */
    setOffline(value: boolean) {
      this.isOffline = value
    },

    /**
     * 处理直接被拉入群聊的 WebSocket 推送。
     * 收到 group_added 后，将群会话直接加入会话列表并显示通知。
     */
    async handleGroupAdded(data: Record<string, unknown>) {
      const conversationId = data.conversationId as string
      const groupData = data.group as {
        id?: number | string
        name?: string
        avatar?: string
        memberAvatars?: Array<{ nickname?: string; avatar?: string }>
      } | undefined
      if (!conversationId || !groupData) return

      // 检查是否已在会话列表中
      const existing = this.sessions.find(s => s.id === conversationId)
      if (existing) return

      // 构建群会话对象
      const session: ChatSession = {
        id: String(groupData.id ?? conversationId),
        name: groupData.name || t('defaults.group'),
        groupName: groupData.name || t('defaults.group'),
        lastMessage: t('chat.joinedGroupLastMessage'),
        time: nowTime(),
        avatarText: (groupData.name || t('defaults.groupChar')).charAt(0) || t('defaults.groupChar'),
        avatarColor: pickGroupColor(groupData.name || t('defaults.groupChar')),
        avatarUrl: normalizeMediaUrl(groupData.avatar),
        memberAvatars: (groupData.memberAvatars || []).slice(0, 9).map((m, i) => ({
          text: (m.nickname || '?').charAt(0) || '?',
          color: pickGroupColor(m.nickname || String(i)),
          imageUrl: normalizeMediaUrl(m.avatar)
        })),
        isGroup: true,
        isReal: true
      }

      this.sessions.unshift(session)
      this.messagesBySession[session.id] = [
        {
          id: `msg-sys-${Date.now()}`,
          sessionId: session.id,
          content: t('chat.systemInvitedJoin'),
          time: nowTime(),
          isSelf: false,
          type: 'system'
        }
      ]

      console.log('[handleGroupAdded] 已加入群会话:', session.name)
    },

    /**
     * 处理群信息变更的 WebSocket 推送。
     * 收到 group_renamed / group_announcement_updated 后，更新本地会话列表中的群信息。
     */
    async handleGroupUpdate(data: Record<string, unknown>) {
      const conversationId = data.conversationId as string
      const groupData = data.group as {
        id?: number | string
        name?: string
        avatar?: string
        announcement?: string
      } | undefined
      if (!conversationId || !groupData) return

      const session = this.sessions.find(s => s.id === conversationId && s.isGroup)
      if (session) {
        // 更新群名称
        if (groupData.name && groupData.name !== session.groupName) {
          session.groupName = groupData.name
          const remark = session.groupRemark || ''
          session.name = remark || groupData.name
          session.avatarText = session.name.charAt(0) || t('defaults.groupChar')
        }
      }

      // 公告变更：强制刷新摘要与列表，避免侧栏残留旧公告
      try {
        const { useGroupMetaStore } = await import('./groupMeta')
        const meta = useGroupMetaStore()
        await Promise.all([
          meta.fetchAnnouncementDisplay(conversationId, true),
          meta.fetchAnnouncements(conversationId, true),
        ])
      } catch (e) {
        console.warn('[handleGroupUpdate] 刷新群公告失败:', e)
      }

      console.log('[handleGroupUpdate] 已更新群信息:', conversationId)
    },

    /**
     * 处理群成员角色变更的 WebSocket 推送。
     * 收到 group_member_role_changed 后，刷新群成员列表。
     */
    async handleGroupMemberRoleChanged(data: Record<string, unknown>) {
      const conversationId = data.conversationId as string
      if (!conversationId) return

      // 刷新群成员缓存，让侧栏/抽屉显示最新成员角色
      try {
        const { useGroupMetaStore } = await import('./groupMeta')
        await useGroupMetaStore().fetchMembers(conversationId, true)
        console.log('[handleGroupMemberRoleChanged] 已刷新群成员列表:', conversationId)
      } catch (e) {
        console.warn('[handleGroupMemberRoleChanged] 刷新群成员失败:', e)
      }
    },

    /**
     * 处理群禁言状态变更的 WebSocket 推送。
     * 收到 group_mute_changed / group_mute_all_changed 后，更新本地禁言状态。
     */
    async handleGroupMuteChanged(data: Record<string, unknown>) {
      const conversationId = data.conversationId as string
      if (!conversationId) return

      try {
        const { useGroupMetaStore } = await import('./groupMeta')
        // 重新获取群详情以获取最新禁言状态
        await useGroupMetaStore().fetchAnnouncement(conversationId)
        console.log('[handleGroupMuteChanged] 已更新群禁言状态:', conversationId)
      } catch (e) {
        console.warn('[handleGroupMuteChanged] 更新禁言状态失败:', e)
      }
    },

    /** 群灵伴接入开关变更 */
    async handleGroupLinkmateChanged(data: Record<string, unknown>) {
      const conversationId = data.conversationId != null ? String(data.conversationId) : ''
      if (!conversationId) return
      const enabled = data.linkmateEnabled === true || data.linkmateEnabled === 1
      try {
        const { useGroupMetaStore } = await import('./groupMeta')
        useGroupMetaStore().linkmateState[conversationId] = { enabled }
      } catch (e) {
        console.warn('[handleGroupLinkmateChanged] 更新灵伴接入状态失败:', e)
      }
    },

    /**
     * 处理朋友圈新动态的 WebSocket 推送。
     * 收到 moments_new_post 后，将新动态添加到朋友圈列表顶部。
     */
    async handleMomentsNewPost(data: Record<string, unknown>) {
      const postData = data.post as {
        id?: number | string
        userId?: number | string
        nickname?: string
        avatar?: string
        content?: string
        images?: string[]
        time?: string
      } | undefined
      if (!postData || !postData.id) return

      try {
        const { useMomentsStore } = await import('./moments')
        const store = useMomentsStore()
        // 避免重复添加（已有则跳过）
        const exists = store.posts.some(p => String(p.id) === String(postData.id))
        if (exists) return

        const momentPost: import('./moments').MomentPost = {
          id: String(postData.id),
          userId: String(postData.userId ?? ''),
          user: postData.nickname || t('defaults.user'),
          avatar: normalizeMediaUrl(postData.avatar) || '',
          content: postData.content || '',
          images: (postData.images || []).map(url => normalizeMediaUrl(url)).filter(Boolean) as string[],
          time: postData.time || t('defaults.justNow'),
          likes: 0,
          liked: false,
          likedBy: [],
          comments: []
        }

        // 添加到列表顶部
        store.posts.unshift(momentPost)
        console.log('[handleMomentsNewPost] 已添加新动态:', momentPost.user)
      } catch (e) {
        console.warn('[handleMomentsNewPost] 处理新动态失败:', e)
      }
    },

    /**
     * 处理群成员加入的 WebSocket 推送。
     * 收到 group_member_added 后，强制刷新对应群的成员列表。
     */
    async handleGroupMemberAdded(data: Record<string, unknown>) {
      const conversationId = data.conversationId as string | undefined
      if (!conversationId) return

      // 即使当前查看的不是该群也要刷新：抽屉里展示的是「群资料」侧的成员列表，
      // 它只读取 groupMeta.members 缓存；缓存命中会直接 return，导致「同意入群」后
      // 已开抽屉依旧只看到旧成员。强制刷新以保证 UI 立即反映新成员。
      try {
        const { useGroupMetaStore } = await import('./groupMeta')
        const groupMetaStore = useGroupMetaStore()
        await groupMetaStore.fetchMembers(conversationId, true)
        console.log('[handleGroupMemberAdded] 已刷新群成员列表:', conversationId)
      } catch (e) {
        console.warn('[handleGroupMemberAdded] 处理群成员加入失败:', e)
      }
    }
  },

  // 持久化关键状态；序列化前经 sanitize 清理。
  // 注意：pinia-plugin-persistedstate v3 使用数组/对象配置，不要用已废弃的 strategies，
  // 否则 paths 不生效，会把 isLoggedIn/isOffline 整包写回 localStorage，导致跳过自动登录且永不连 WS。
  // messagesBySession 单独用 sessionStorage，避免聊天记录撑爆 localStorage。
  persist: [
    {
      key: 'linkx-app',
      storage: localStorage,
      paths: [
        'sessions',
        'currentSessionId',
        'theme',
        'userProfile',
        'savedLogin',
        'navKey'
      ],
      serializer: {
        // 读写都清洗：去掉 MinIO 预签名，避免过期头像落盘；登录后接口会重签
        serialize: value => JSON.stringify(sanitizeAppPersistState(value as Record<string, unknown>)),
        deserialize: value =>
          sanitizeAppPersistState(JSON.parse(value) as Record<string, unknown>)
      },
      afterRestore: ({ store }) => {
        // 兼容历史脏数据：旧版误持久化的离线标记一律清掉
        store.isOffline = false
      }
    },
    {
      key: 'linkx-app-msgs',
      storage: sessionStorage,
      paths: ['messagesBySession'],
      serializer: {
        serialize: value => {
          if (!useAppSettingsStore().retainChatCache) {
            return JSON.stringify({ messagesBySession: {} })
          }
          return JSON.stringify(sanitizeAppPersistState(value as Record<string, unknown>))
        },
        deserialize: value => {
          if (!useAppSettingsStore().retainChatCache) {
            return { messagesBySession: {} }
          }
          return sanitizeAppPersistState(JSON.parse(value) as Record<string, unknown>)
        }
      }
    }
  ]
})
