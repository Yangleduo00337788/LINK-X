/**
 * 应用核心 Store（app）
 * 全局导航、聊天会话/消息、主题、登录态、锁屏与用户资料等核心业务状态
 */

// Pinia Store 工厂
import { defineStore } from 'pinia'
// 核心业务类型
import type { NavKey, ChatSession, ChatMessage, ContactItem } from '../types'
// Mock 初始会话、消息及联系人转会话工具
import { initialSessions, initialMessages, sessionFromContact } from '../data/mockData'
// 通讯录 Store（加群/加好友后同步联系人）
import { useContactsStore } from './contacts'
// 群元数据 Store（邀请成员等）
import { useGroupMetaStore } from './groupMeta'
// 主题同步到 document 与 Electron 主进程
import { applyDocumentTheme, notifyElectronTheme } from '../utils/themeSync'
// 持久化前清理敏感或过大字段
import { sanitizeAppPersistState } from '../utils/persistSanitize'
// 登出时重置其它 UI Store
import { resetSessionUi, cleanupNaiveUiOverlays } from '../utils/resetSessionUi'
// HTTP 客户端与认证 API
import * as authApi from '../api/auth'
import * as userApi from '../api/user'
import { clearTokens, getRefreshToken, hasRefreshToken, saveTokenPair } from '../utils/tokenStorage'
import { hasLockPin as isLockPinConfigured, verifyLockPin as verifyLockPinHash, saveLockPinHash } from '../utils/lockPin'
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
}

/** 记住账号 / 自动登录（不存储密码） */
export interface SavedLogin {
  username: string
  rememberMe: boolean
  autoLogin: boolean
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

// 定义并导出 app Store
export const useAppStore = defineStore('app', {
  // 应用全局初始状态
  state: () => ({
    navKey: 'chat' as NavKey,                                    // 当前主导航模块
    sessions: [...initialSessions] as ChatSession[],             // 会话列表
    messagesBySession: { ...initialMessages } as Record<string, ChatMessage[]>, // 各会话消息映射
    currentSessionId: null as string | null,                     // 当前打开的会话 id
    theme: 'light' as 'light' | 'dark',                          // 明暗主题
    contactsActiveView: 'none' as 'none' | 'friend-notifs' | 'group-notifs', // 通讯录子视图
    userProfile: {
      nickname: '',           // 登录后由后端返回
      signature: '编辑个性签名',
      avatar: '',             // 头像 URL
      userId: 0               // 用户 ID
    },
    isLoggedIn: false,   // 是否已登录
    isLoading: false,    // 登录等异步操作加载中
    authInitializing: false, // 仅在 Refresh Token 自动登录期间为 true
    isOffline: false,    // 离线模式（演示用）
    isLocked: false,     // 是否处于锁屏状态
    savedLogin: {
      username: '',
      rememberMe: true,
      autoLogin: false
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
      if (!this.messagesBySession[session.id]) {
        this.messagesBySession[session.id] = [] // 懒初始化空消息列表
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
    startChatWithContact(contact: ContactItem) {
      const existing = this.sessions.find(
        s => !s.isGroup && (s.id === contact.id || s.name === contact.name)
      )
      if (existing) {
        this.selectSession(existing)
        this.navKey = 'chat'
        return
      }
      this.ensureSession(sessionFromContact(contact))
    },

    /**
     * 创建群聊并写入系统欢迎消息
     * @param members 初始成员（不含自己）
     * @param groupName 可选群名
     * @returns 新群会话，无成员时返回 null
     */
    createGroup(members: CreateGroupMember[], groupName?: string) {
      if (members.length === 0) return null
      const id = `group-${Date.now()}`
      const time = nowTime()
      // 群名：传入名 > 2 人以内成员名拼接 > 默认「群聊（N人）」
      const name =
        groupName?.trim() ||
        (members.length <= 2
          ? members.map(m => m.name).join('、')
          : `群聊（${members.length + 1}人）`)
      const session: ChatSession = {
        id,
        name,
        lastMessage: '系统：欢迎加入群聊',
        time,
        avatarText: name.charAt(0) || '群',
        avatarColor: pickGroupColor(name),
        isGroup: true
      }
      // 首条系统消息：谁发起了群聊
      this.messagesBySession[id] = [
        {
          id: `msg-sys-${Date.now()}`,
          sessionId: id,
          content: `系统：${this.userProfile.nickname} 发起了群聊`,
          time,
          isSelf: false,
          type: 'system'
        }
      ]
      this.ensureSession(session)
      return session
    },

    /**
     * 加入已有名称的群（若本地无则创建）
     * @param groupName 群名称
     */
    joinGroup(groupName: string) {
      const exists = this.sessions.find(s => s.isGroup && s.name === groupName)
      if (exists) {
        this.selectSession(exists)
        this.navKey = 'chat'
        return exists
      }
      const id = `group-join-${Date.now()}`
      const time = nowTime()
      const session: ChatSession = {
        id,
        name: groupName,
        lastMessage: '系统：欢迎加入群聊',
        time,
        avatarText: groupName.charAt(0) || '群',
        avatarColor: pickGroupColor(groupName),
        isGroup: true
      }
      this.messagesBySession[id] = [
        {
          id: `msg-sys-${Date.now()}`,
          sessionId: id,
          content: '系统：你已加入群聊',
          time,
          isSelf: false,
          type: 'system'
        }
      ]
      this.ensureSession(session)
      useContactsStore().syncFriendFromSession(session) // 同步到通讯录（演示逻辑）
      return session
    },

    /**
     * 添加好友并创建对应单聊会话
     * @param name 好友名称
     */
    addFriendSession(name: string) {
      const id = `friend-${Date.now()}`
      const time = nowTime()
      const session: ChatSession = {
        id,
        name,
        lastMessage: '我们已经是好友了，开始聊天吧',
        time,
        avatarText: name.charAt(0) || '?',
        avatarColor: pickGroupColor(name),
        online: true
      }
      this.messagesBySession[id] = [
        {
          id: `msg-sys-${Date.now()}`,
          sessionId: id,
          content: '系统：你们已成为好友',
          time,
          isSelf: false,
          type: 'system'
        }
      ]
      this.ensureSession(session)
      useContactsStore().syncFriendFromSession(session)
      return session
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
      if (this.currentSessionId === sessionId) {
        this.currentSessionId = this.sessions[0]?.id ?? null
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
     * 邀请成员入群：更新 groupMeta 成员列表并插入系统消息
     * @param sessionId 群会话 id
     * @param names 被邀请人姓名列表
     */
    inviteGroupMembers(sessionId: string, names: string[]) {
      if (!names.length) return
      useGroupMetaStore().addMembers(sessionId, names)
      const time = nowTime()
      const text = `系统：${this.userProfile.nickname} 邀请了 ${names.join('、')} 加入群聊`
      if (!this.messagesBySession[sessionId]) {
        this.messagesBySession[sessionId] = []
      }
      this.messagesBySession[sessionId].push({
        id: `msg-sys-${Date.now()}`,
        sessionId,
        content: text,
        time,
        isSelf: false,
        type: 'system'
      })
      const session = this.sessions.find(s => s.id === sessionId)
      if (session) {
        session.lastMessage = text
        session.time = time
      }
    },

    /** 退群：等价于删除该群会话 */
    leaveGroup(sessionId: string) {
      this.deleteSession(sessionId)
    },

    /**
     * 向当前会话发送消息（支持多种类型与附件字段）
     * @param content 文本内容或占位
     * @param options 类型与扩展字段
     */
    sendMessage(content: string, options: SendMessageOptions = {}) {
      const id = this.currentSessionId
      if (!id) return // 无当前会话

      const session = this.sessions.find(s => s.id === id)
      if (session?.blocked) return // 已屏蔽则不发送

      const type = options.type ?? 'text'
      const trimmed = content.trim()

      // 各类型必填校验
      if (type === 'text' && !trimmed) return
      if (type === 'image' && !trimmed) return
      if (type === 'voice' && !options.voiceDuration) return
      if (type === 'redPacket' && !options.redPacketAmount) return

      const time = nowTime()
      const isImage = options.isImage ?? type === 'image'

      const msg: ChatMessage = {
        id: `msg-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
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
        isSelf: true,
        type,
        replyTo: options.replyTo,
        fileName: options.fileName,
        fileSize: options.fileSize,
        fileUrl: options.fileUrl,
        isImage,
        fileStatus: type === 'file' ? '已发送' : undefined,
        voiceDuration: options.voiceDuration,
        voiceUrl: options.voiceUrl,
        redPacketGreeting: options.redPacketGreeting,
        redPacketAmount: options.redPacketAmount,
        redPacketOpened: type === 'redPacket' ? false : undefined
      }

      if (!this.messagesBySession[id]) {
        this.messagesBySession[id] = []
      }
      this.messagesBySession[id].push(msg)

      // 更新会话列表预览
      if (session) {
        session.lastMessage = messagePreview(msg)
        session.time = time
      }
    },

    /**
     * 撤回（删除）当前会话中的某条消息
     * 若撤回的是最后一条，则回退会话 preview
     * @param messageId 消息 id
     * @returns 是否成功
     */
    recallMessage(messageId: string) {
      const sessionId = this.currentSessionId
      if (!sessionId) return false

      const msgs = this.messagesBySession[sessionId]
      if (!msgs) return false

      const index = msgs.findIndex(m => m.id === messageId)
      if (index === -1) return false

      const wasLast = index === msgs.length - 1
      msgs.splice(index, 1)

      if (wasLast) {
        const session = this.sessions.find(s => s.id === sessionId)
        // 优先取最后一条非系统消息作为 preview
        const last = msgs.filter(m => m.type !== 'system').pop() ?? msgs[msgs.length - 1]
        if (session) {
          session.lastMessage = last ? messagePreview(last) : ''
          if (last) session.time = last.time
        }
      }

      return true
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
        const res = await userApi.updateProfile({ signature: text })
        if (res.code === 200 && res.data) {
          this.userProfile.signature = res.data.signature || text
        } else {
          this.userProfile.signature = text
        }
      } catch {
        this.userProfile.signature = text
      }
    },

    /** 更新昵称（本地+后端） */
    async updateNickname(name: string) {
      try {
        const res = await userApi.updateProfile({ nickname: name })
        if (res.code === 200 && res.data) {
          this.userProfile.nickname = res.data.nickname || name
        } else {
          this.userProfile.nickname = name
        }
      } catch {
        this.userProfile.nickname = name
      }
    },

    /** 更新头像 */
    async updateAvatar(file: File) {
      const res = await userApi.uploadAvatar(file)
      if (res.code === 200 && res.data) {
        this.userProfile.avatar = res.data
        return res.data
      }
      throw new Error(res.message || '上传失败')
    },

    /** 获取当前用户信息 */
    async fetchCurrentUser() {
      try {
        const res = await userApi.getCurrentUser()
        if (res.code === 200 && res.data) {
          this.userProfile = {
            nickname: res.data.nickname || res.data.username,
            signature: res.data.signature || '编辑个性签名',
            avatar: res.data.avatar || '',
            userId: res.data.id
          }
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

      resetSessionUi()
      this.isLocked = false
      this.isLoggedIn = false
      this.userProfile.nickname = ''
      this.userProfile.signature = '编辑个性签名'
      this.userProfile.avatar = ''
      this.userProfile.userId = 0
      if (!this.savedLogin.rememberMe) {
        this.savedLogin.username = ''
        this.savedLogin.autoLogin = false
      }
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

          this.userProfile.nickname = user.nickname || user.username
          this.userProfile.signature = user.signature || '编辑个性签名'
          this.userProfile.avatar = user.avatar || ''
          this.userProfile.userId = user.id

          this.savedLogin.username = rememberMe ? username : ''
          this.savedLogin.rememberMe = rememberMe
          this.savedLogin.autoLogin = autoLogin

          this.isLoggedIn = true
          return true
        }
        throw new Error(res.message || '登录失败')
      } finally {
        this.isLoading = false
      }
    },

    /**
     * 启动时用 Refresh Token 恢复会话（须开启自动登录且存在 refreshToken）
     */
    async tryAutoLogin() {
      if (this.isLoggedIn) return
      if (!this.savedLogin.autoLogin || !this.savedLogin.rememberMe || !this.savedLogin.username) {
        return
      }
      if (!(await hasRefreshToken())) {
        return
      }

      this.authInitializing = true
      this.isLoading = true
      try {
        const refresh = await getRefreshToken()
        if (!refresh) return
        const res = await authApi.refreshToken(refresh)
        if (res.code === 200 && res.data) {
          await saveTokenPair(res.data.accessToken, res.data.refreshToken)
          const user = res.data.user
          this.userProfile.nickname = user.nickname || user.username
          this.userProfile.signature = user.signature || '编辑个性签名'
          this.userProfile.avatar = user.avatar || ''
          this.userProfile.userId = user.id
          this.isLoggedIn = true
        }
      } catch {
        await clearTokens()
        this.isLoggedIn = false
      } finally {
        this.isLoading = false
        this.authInitializing = false
      }
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
    },

    /**
     * 模拟收到一条他人文本消息（演示通知与未读数）
     * 仅在当前有选中会话时生效
     */
    simulateIncomingMessage() {
      const id = this.currentSessionId
      if (!id) return
      const time = nowTime()
      const msg: ChatMessage = {
        id: `msg-${Date.now()}`,
        sessionId: id,
        content: '这是一条模拟的测试消息',
        time,
        isSelf: false,
        type: 'text'
      }

      if (!this.messagesBySession[id]) {
        this.messagesBySession[id] = []
      }
      this.messagesBySession[id].push(msg)

      const session = this.sessions.find(s => s.id === id)
      if (session) {
        session.lastMessage = msg.content
        session.time = time
        if (!session.muted) {
          session.unread = (session.unread || 0) + 1 // 非免打扰才增未读
        }

        // 浏览器系统通知
        if (window.Notification && Notification.permission === 'granted') {
          new Notification(session.name, {
            body: msg.content,
            silent: false
          })
        } else if (window.Notification && Notification.permission !== 'denied') {
          Notification.requestPermission().then(permission => {
            if (permission === 'granted') {
              new Notification(session.name, {
                body: msg.content,
                silent: false
              })
            }
          })
        }
      }
    }
  },

  // 持久化关键状态；序列化前经 sanitize 清理
  persist: {
    key: 'linkx-app',
    paths: [
      'sessions',
      'messagesBySession',
      'currentSessionId',
      'theme',
      'userProfile',
      'savedLogin',
      'navKey',
      'isOffline'
    ],
    serializer: {
      serialize: value => JSON.stringify(sanitizeAppPersistState(value as Record<string, unknown>)),
      deserialize: value => JSON.parse(value)
    }
  }
})
