/**
 * 应用核心 Store（app）
 * 全局导航、聊天会话/消息、主题、登录态、锁屏与用户资料等核心业务状态
 */

// Pinia Store 工厂
import { defineStore } from 'pinia'
// 核心业务类型
import type { NavKey, ChatSession, ChatMessage, ContactItem } from '../types'
import * as chatApi from '../api/chat'
import {
  connectChatSocket,
  disconnectChatSocket,
  sendChatMessage,
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
  contentMentionsUser,
  notifyIncomingMessage,
  shouldAlertForSession
} from '../utils/messageNotify'
import { useAppSettingsStore } from './appSettings'
import { dataUrlToFile } from '../utils/fileConvert'
import { generateUuidV4 } from '../utils/parseJson'
import type { MessageItem } from '../types/chat'
import { normalizeMediaUrl } from '../utils/mediaUrl'
// 通讯录 Store（加群/加好友后同步联系人）
import { useContactsStore } from './contacts'
// 群元数据 Store（邀请成员等）
import { useGroupMetaStore } from './groupMeta'
import type { GroupMember } from './groupMeta'
// 主题同步到 document 与 Electron 主进程
import { applyDocumentTheme, notifyElectronTheme } from '../utils/themeSync'
// 持久化前清理敏感或过大字段
import { sanitizeAppPersistState } from '../utils/persistSanitize'
// 登出时重置其它 UI Store
import { resetSessionUi, cleanupNaiveUiOverlays } from '../utils/resetSessionUi'
import { useNotificationsStore } from './notifications'
// HTTP 客户端与认证 API
import * as authApi from '../api/auth'
import * as userApi from '../api/user'
import * as groupApi from '../api/group'
import type { UpdateProfileRequest } from '../api/user'
import { clearTokens, getRefreshToken, hasRefreshToken, saveTokenPair } from '../utils/tokenStorage'
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
}

/** 群头像可选背景色池 */
const GROUP_COLORS = ['#12b7f5', '#52c41a', '#722ed1', '#fa8c16', '#eb2f96', '#13c2c2']

/**
 * 根据消息类型生成会话列表「最后一条消息」预览文案
 * @param msg 聊天消息
 */
function messagePreview(msg: ChatMessage): string {
  if (msg.type === 'file') return `[文件] ${msg.fileName || msg.content}`
  if (msg.type === 'image' || msg.isImage) return '[图片]'
  if (msg.type === 'voice') return '[语音]'
  if (msg.type === 'redPacket') return `[红包] ${msg.redPacketGreeting || '恭喜发财'}`
  if (msg.type === 'recall') return '撤回了一条消息'
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

/** 将后端用户资料写入 store 的 userProfile 结构 */
function mapApiProfile(data: ProfileSource) {
  const gender = data.gender === '女' ? '女' : '男'
  // birthday 可能是字符串（ISO）或数字（毫秒时间戳），统一转为数字
  let birthday: number | null = null
  if (data.birthday != null && data.birthday !== '') {
    if (typeof data.birthday === 'number') {
      birthday = data.birthday
    } else {
      const parsed = Date.parse(data.birthday)
      birthday = isNaN(parsed) ? null : parsed
    }
  }
  return {
    nickname: data.nickname || data.username || '',
    username: data.username || '',
    signature: data.signature?.trim() ? data.signature : '编辑个性签名',
    avatar: normalizeMediaUrl(data.avatar) || '',
    userId: sanitizeUserId(data.id),
    gender: gender as '男' | '女',
    birthday,
    country: data.country || '中国',
    province: data.province || '',
    region: data.region || '',
    email: data.email || null,
    emailBound: !!data.emailBound,
    phone: data.phone || null,
    phoneBound: !!data.phoneBound
  }
}

// 定义并导出 app Store
export const useAppStore = defineStore('app', {
  // 应用全局初始状态
  state: () => ({
    navKey: 'chat' as NavKey,                                    // 当前主导航模块
    sessions: [] as ChatSession[],                                // 会话列表（从后端加载）
    messagesBySession: {} as Record<string, ChatMessage[]>,       // 各会话消息映射（从后端加载）
    currentSessionId: null as string | null,                     // 当前打开的会话 id
    theme: 'light' as 'light' | 'dark',                          // 明暗主题
    contactsActiveView: 'none' as 'none' | 'friend-notifs' | 'group-notifs', // 通讯录子视图
    userProfile: {
      nickname: '',
      username: '',
      signature: '编辑个性签名',
      avatar: '',
      userId: '',
      gender: '男' as '男' | '女',
      birthday: null as number | null,
      country: '中国',
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
    /** 会话列表：置顶项排在前面 */
    sortedSessions(state): ChatSession[] {
      return [...state.sessions].sort((a, b) => {
        if (a.pinned && !b.pinned) return -1
        if (!a.pinned && b.pinned) return 1
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
      const s = this.sessions.find(x => x.id === session.id)
      if (s?.unread) {
        s.unread = 0 // 进入会话即清未读
      }
      if (s?.atMe) {
        // 进入会话清除列表角标；若尚无跳转目标，加载后从消息里找回
        const needRecover = !s.atMeMessageId
        s.atMe = false
        if (session.isReal) {
          void this.loadSessionMessages(session.id).then(() => {
            if (needRecover) this.recoverAtMeMessageId(session.id)
          })
        } else if (needRecover) {
          this.recoverAtMeMessageId(session.id)
        }
      } else if (session.isReal) {
        void this.loadSessionMessages(session.id)
      }
      if (!this.messagesBySession[session.id]) {
        this.messagesBySession[session.id] = [] // 懒初始化空消息列表
      }
    },

    /** 清除会话的 @我 跳转提示（对话框内跳转后或已看见时调用） */
    clearAtMeMessage(sessionId: string) {
      const s = this.sessions.find(x => x.id === sessionId)
      if (!s) return
      s.atMe = false
      s.atMeMessageId = undefined
    },

    /** 从已加载消息中找回最近一条 @我，写入 atMeMessageId */
    recoverAtMeMessageId(sessionId: string) {
      const s = this.sessions.find(x => x.id === sessionId)
      if (!s?.isGroup || s.atMeMessageId) return
      const names = [this.userProfile.nickname, this.userProfile.username]
      const list = this.messagesBySession[sessionId] || []
      for (let i = list.length - 1; i >= 0; i--) {
        const m = list[i]
        if (m.isSelf || m.type === 'system' || m.type === 'time' || m.type === 'recall') continue
        if (contentMentionsUser(m.content, names)) {
          s.atMeMessageId = m.id
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
      // 优先按 id 匹配；单聊还可按名称匹配（避免重复会话）
      const exists =
        this.sessions.find(s => s.id === session.id) ??
        (!session.isGroup
          ? this.sessions.find(s => !s.isGroup && s.name === session.name)
          : undefined)
      if (exists) {
        this.selectSession(exists)
        this.navKey = 'chat'
        return exists
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
          : `群聊（${members.length + 1}人）`)

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
                    text: this.userProfile.nickname?.charAt(0) || '我',
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
            lastMessage: '系统：欢迎加入群聊',
            time: nowTime(),
            avatarText: (groupConv.name || name).charAt(0) || '群',
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
              content: `系统：${this.userProfile.nickname} 发起了群聊`,
              time: nowTime(),
              isSelf: false,
              type: 'system'
            }
          ]
          this.ensureSession(session)
          return session
        }
        throw new Error(res.message || '创建群聊失败')
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

      throw new Error('群聊不存在或你尚未加入')
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
        // 确保登录后不自动选中任何会话
        this.currentSessionId = null
        await this.loadChatSessions()
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
        // 保留本地未读 / @我 / 免打扰 / 置顶（服务端列表暂不带回这些字段）
        this.sessions = realSessions.map(s => {
          const prev = prevById.get(s.id)
          if (!prev) return s
          return {
            ...s,
            unread: prev.unread || s.unread,
            atMe: prev.atMe,
            atMeMessageId: prev.atMeMessageId,
            muted: prev.muted,
            pinned: prev.pinned
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
      if (this.messagesLoaded[sessionId] || this.messagesLoading[sessionId]) return
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

          // 合并：服务端消息 + 本地乐观消息
          const merged = [...serverMessages, ...localOnlyMessages]
          // 按时间排序（时间格式 HH:mm 可以直接字符串比较）
          merged.sort((a, b) => (a.time > b.time ? 1 : -1))

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
        }
      } catch (e) {
        console.error('加载历史消息失败:', e)
      } finally {
        this.messagesLoading[sessionId] = false
      }
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
            this.messagesBySession[sessionId] = [...unique, ...existing]
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
          if (session.name === '好友' && name) {
            session.name = name
            session.avatarText = name.charAt(0) || '?'
          }
          this.ensureSession(session)
          await this.loadSessionMessages(session.id)
          return session
        }
        throw new Error(res.message || '打开会话失败')
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
        onError: (code: number, msg: string) => {
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
            useCallStore().handleRemoteEvent(action, data as import('../api/call').CallEventPayload)
          })
        },
        onCustomAction: (action: string, data: Record<string, unknown>) => {
          if (action === 'notification_refresh') {
            void import('./notifications').then(({ useNotificationsStore }) => {
              void useNotificationsStore().refreshFromSocket(data)
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

    /** 处理 WebSocket 推送的新消息 */
    handleIncomingWsMessage(message: MessageItem) {
      // 后端 MessageVO.conversationId 是 Long 类型，JSON 反序列化后是数字
      // 而前端会话 id 是字符串，需要统一为字符串进行比较和查找
      const sessionId = String(message.conversationId)
      const chatMsg = messageToChatMessage(message, sessionId)

      if (!this.messagesBySession[sessionId]) {
        this.messagesBySession[sessionId] = []
      }
      const exists = this.messagesBySession[sessionId].some(m => m.id === chatMsg.id)
      if (!exists) {
        this.messagesBySession[sessionId].push(chatMsg)
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
            !message.isSelf &&
            contentMentionsUser(message.content, [
              this.userProfile.nickname,
              this.userProfile.username
            ])
          // 记录待跳转消息；当前会话是否展示浮层由 ChatPanel 根据是否贴底决定
          if (mentioned) {
            session.atMeMessageId = chatMsg.id
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
            if (mentioned) {
              session.atMe = true
            }
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
      const sessionId = String(message.conversationId)

      console.log('[handleWsAck]', { clientMsgId, sessionId, messageId: message.id })

      // 确保消息列表存在（如果没有，先初始化）
      if (!this.messagesBySession[sessionId]) {
        console.log('[handleWsAck] 初始化消息列表:', sessionId)
        this.messagesBySession[sessionId] = []
      }

      const index = this.messagesBySession[sessionId].findIndex(m => m.id === clientMsgId)
      const chatMsg = messageToChatMessage(message, sessionId)

      console.log('[handleWsAck] 查找结果:', { index, msgsCount: this.messagesBySession[sessionId].length, clientMsgId })

      if (index >= 0) {
        console.log('[handleWsAck] 替换乐观消息:', { index, clientMsgId, newId: message.id })
        // 使用 splice 直接操作 store 属性，触发 Vue 响应式更新
        this.messagesBySession[sessionId].splice(index, 1, chatMsg)
      } else {
        const exists = this.messagesBySession[sessionId].some(m => m.id === chatMsg.id)
        console.log('[handleWsAck] 检查重复:', { exists, chatMsgId: chatMsg.id })
        if (!exists) {
          console.log('[handleWsAck] 添加新消息到列表')
          this.messagesBySession[sessionId].push(chatMsg)
        }
      }

      const session = this.sessions.find(s => s.id === sessionId)
      if (session) {
        session.lastMessage = messagePreviewFromItem(message)
        session.time = chatMsg.time
      }
    },

    /** 重置聊天相关状态（登出时） */
    resetChatState() {
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

    /** 切换会话置顶状态 */
    toggleSessionPin(sessionId: string) {
      const s = this.sessions.find(x => x.id === sessionId)
      if (s) s.pinned = !s.pinned
    },

    /** 切换会话免打扰 */
    toggleSessionMute(sessionId: string) {
      const s = this.sessions.find(x => x.id === sessionId)
      if (s) s.muted = !s.muted
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

    /** 切换会话拉黑/屏蔽状态（屏蔽后无法发送） */
    toggleSessionBlock(sessionId: string) {
      const s = this.sessions.find(x => x.id === sessionId)
      if (s) s.blocked = !s.blocked
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
          await useGroupMetaStore().fetchMembers(sessionId)
          return true
        }
        throw new Error(res.message || '邀请成员失败')
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
          throw new Error(res.message || '退出群聊失败')
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
        throw new Error(res.message || '转让群主失败')
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
        throw new Error(res.message || '更新成员角色失败')
      }
      await useGroupMetaStore().fetchMembers(sessionId, true)
    },

    /**
     * 解散群聊：调用真实后端 {@code DELETE /group/{id}}（仅群主）。
     */
    async dissolveGroup(sessionId: string): Promise<void> {
      const res = await groupApi.dissolveGroup(sessionId)
      if (res.code !== 200) {
        throw new Error(res.message || '解散群聊失败')
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
        throw new Error(res.message || '移除成员失败')
      }
      await useGroupMetaStore().fetchMembers(sessionId)
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
            ? (options.fileName || trimmed || '文件')
            : type === 'voice'
              ? '[语音消息]'
              : type === 'redPacket'
                ? (options.redPacketGreeting || trimmed || '恭喜发财')
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
        fileStatus: type === 'file' ? '发送中...' : undefined,
        voiceDuration: options.voiceDuration,
        voiceUrl: options.voiceUrl,
        redPacketGreeting: options.redPacketGreeting,
        redPacketAmount: options.redPacketAmount,
        redPacketOpened: type === 'redPacket' ? false : undefined
      }

      if (!this.messagesBySession[id]) {
        this.messagesBySession[id] = []
      }
      this.messagesBySession[id].push(optimistic)

      if (session) {
        session.lastMessage = messagePreview(optimistic)
        session.time = time
      }

      try {
        let fileUrl = options.fileUrl
        let fileName = options.fileName
        let fileSizeNum: number | undefined

        if (type === 'image' || type === 'file') {
          let uploadFile: File | null = options.rawFile ?? null
          console.log('[发送消息] 准备上传文件:', { type, hasRawFile: !!uploadFile, rawFileSize: uploadFile?.size, rawFileName: uploadFile?.name, contentLength: content.length, contentPrefix: content.substring(0, 50) })
          if (!uploadFile && type === 'image' && content.startsWith('data:')) {
            uploadFile = dataUrlToFile(content, 'image.png')
            console.log('[发送消息] 从 data URL 创建文件')
          }
          // 注意：不再从 blob URL 重建 File，直接使用 rawFile，避免 blob 失效导致 size=0
          if (uploadFile) {
            console.log('[发送消息] 上传文件:', uploadFile.name, uploadFile.size, uploadFile.type, 'lastModified:', uploadFile.lastModified)
            const uploadRes = await chatApi.uploadChatFile(id, uploadFile)
            console.log('[发送消息] 上传结果:', uploadRes)
            if (uploadRes.code !== 200 || !uploadRes.data) {
              throw new Error(uploadRes.message || '文件上传失败')
            }
            fileUrl = uploadRes.data.url
            fileName = uploadRes.data.fileName || uploadFile.name
            // fileSize 可能是 number 或 string，统一转为 number
            const sizeValue = uploadRes.data.fileSize
            fileSizeNum = typeof sizeValue === 'string' ? Number(sizeValue) || 0 : sizeValue
          }
        }

        if (type === 'image' && !options.rawFile && !content.startsWith('data:') && !fileUrl) {
          fileUrl = trimmed || content
        }

        if ((type === 'image' || type === 'file') && !fileUrl) {
          throw new Error('文件上传失败')
        }

        if (!isChatSocketConnected()) {
          await this.connectChatWebSocket()
        }
        await ensureChatSocketConnected()

        sendChatMessage({
          action: 'send',
          clientMsgId,
          conversationId: id,
          msgType: type === 'text' ? 'text' : type === 'image' ? 'image' : type === 'voice' ? 'voice' : 'file',
          content: type === 'text' ? trimmed : fileUrl,
          fileName,
          fileSize: fileSizeNum,
          fileUrl,
          voiceDuration: type === 'voice' ? options.voiceDuration : undefined
        })
      } catch (e) {
        const msgs = this.messagesBySession[id]
        const idx = msgs?.findIndex(m => m.id === clientMsgId) ?? -1
        if (idx >= 0) msgs?.splice(idx, 1)
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
          throw new Error(res.message || '撤回失败')
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
      chatMsg.content = '撤回了一条消息'
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

    /** 更新个性签名（本地+后端） */
    async updateSignature(text: string) {
      try {
        await this.updateProfile({ signature: text })
      } catch {
        this.userProfile.signature = text
      }
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
    },

    /** 更新用户资料（昵称、签名、性别、生日、地区等） */
    async updateProfile(payload: UpdateProfileRequest) {
      try {
        const res = await userApi.updateProfile(payload)
        if (res.code === 200 && res.data) {
          this.applyUserProfile(res.data)
          return res.data
        }
        throw new Error(res.message || '更新失败')
      } catch (error) {
        // 网络失败时仍更新本地已提交的字段，避免用户感知丢失
        if (payload.nickname !== undefined) {
          this.userProfile.nickname = payload.nickname
          if (this.savedLogin.rememberMe) this.savedLogin.nickname = payload.nickname
        }
        if (payload.signature !== undefined) this.userProfile.signature = payload.signature
        if (payload.gender !== undefined) {
          this.userProfile.gender = payload.gender === '女' ? '女' : '男'
        }
        if (payload.birthday !== undefined) {
          // birthday 可能是 string 或 number，统一转为 number | null
          const b = payload.birthday
          if (b == null || b === '') {
            this.userProfile.birthday = null
          } else if (typeof b === 'number') {
            this.userProfile.birthday = b
          } else {
            const parsed = Date.parse(b)
            this.userProfile.birthday = isNaN(parsed) ? null : parsed
          }
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
        this.userProfile.avatar = normalizeMediaUrl(res.data)
        if (this.savedLogin.rememberMe) {
          this.savedLogin.avatar = this.userProfile.avatar
        }
        return this.userProfile.avatar
      }
      throw new Error(res.message || '上传失败')
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
      useContactsStore().reset()
      useNotificationsStore().resetFriends()
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
      this.userProfile.signature = '编辑个性签名'
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
        throw new Error(res.message || '登录失败')
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
      const startedAt = Date.now()
      const minLoadingMs = 1000
      let outcome: 'ok' | 'offline' | 'failed' = 'failed'
      try {
        if (!(await hasRefreshToken())) {
          this.savedLogin.autoLogin = false
          return 'failed'
        }

        const refresh = await getRefreshToken()
        if (!refresh) {
          this.savedLogin.autoLogin = false
          return 'failed'
        }

        const res = await authApi.refreshToken(refresh)
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
        const elapsed = Date.now() - startedAt
        if (elapsed < minLoadingMs) {
          await new Promise<void>(resolve => {
            setTimeout(resolve, minLoadingMs - elapsed)
          })
        }
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

    /** 进入锁屏 */
    lock() {
      this.isLocked = true
    },

    /** 解除锁屏 */
    unlock() {
      this.isLocked = false
    },

    /** 切换离线模式开关 */
    toggleOffline() {
      this.isOffline = !this.isOffline
    },

    /** 显式设置离线状态 */
    setOffline(value: boolean) {
      this.isOffline = value
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
        serialize: value => JSON.stringify(sanitizeAppPersistState(value as Record<string, unknown>)),
        deserialize: value => JSON.parse(value)
      },
      afterRestore: ({ store }) => {
        // 兼容历史脏数据：旧版误持久化的离线标记一律清掉
        store.isOffline = false
      }
    },
    {
      key: 'linkx-app-msgs',
      storage: sessionStorage,
      paths: ['messagesBySession']
    }
  ]
})
