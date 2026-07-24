/**
 * 应用偏好设置 Store
 * 管理通知、隐私、语言、聊天背景、提示音等用户可配置项
 * <p>
 * 持久化策略：
 * - 本地 localStorage（pinia-plugin-persistedstate）作为离线缓存，保证登录前/断网时可用；
 * - 登录成功后由服务端覆盖（loadFromServer）；
 * - 用户切换开关时通过 debounce 800ms 自动保存到服务端。
 * </p>
 * <p>
 * 事件总线：
 * - 每次成功/失败保存会通过 mitt 风格 on/off/emit 广播，组件可订阅 toast；
 * - 这样组件不直接 await Promise，只 @update:value 即可触发保存与提示。
 * </p>
 */

import { defineStore } from 'pinia'
import type { ChatBackgroundId } from '../types'
import * as preferenceApi from '../api/preference'
import { onPreferenceChange } from '../utils/preferenceEvents'
import { setLocale } from '../i18n'

// 服务端偏好字段 → 本地 state 字段 一一对应
type SyncableKey =
  | 'autoStart'
  | 'soundNotify'
  | 'messageDetail'
  | 'notifyAtMe'
  | 'notifySound'
  | 'privacyVerifyFriend'
  | 'privacyAllowStranger'
  | 'privacyShowOnline'
  | 'language'
  | 'chatBackground'
  | 'notifyTone'
  | 'favoritesViewMode'
  | 'favoritesSort'
  | 'quietHoursEnabled'
  | 'quietHoursStart'
  | 'quietHoursEnd'
  | 'notifyChat'
  | 'notifySocial'
  | 'notifyMoments'
  | 'notifySystem'

const SYNCABLE_KEYS: SyncableKey[] = [
  'autoStart',
  'soundNotify',
  'messageDetail',
  'notifyAtMe',
  'notifySound',
  'privacyVerifyFriend',
  'privacyAllowStranger',
  'privacyShowOnline',
  'language',
  'chatBackground',
  'notifyTone',
  'favoritesViewMode',
  'favoritesSort',
  'quietHoursEnabled',
  'quietHoursStart',
  'quietHoursEnd',
  'notifyChat',
  'notifySocial',
  'notifyMoments',
  'notifySystem'
]

// 字段到"中文显示名"的映射，供 toast 文案使用
const FIELD_LABEL: Record<SyncableKey, string> = {
  autoStart: '开机自动启动',
  soundNotify: '新消息声音提示',
  messageDetail: '通知显示消息详情',
  notifyAtMe: '群聊 @ 我',
  notifySound: '通知声音',
  privacyVerifyFriend: '加好友需验证',
  privacyAllowStranger: '允许陌生人会话',
  privacyShowOnline: '在线状态可见',
  language: '界面语言',
  chatBackground: '聊天背景',
  notifyTone: '提示音',
  favoritesViewMode: '收藏视图',
  favoritesSort: '收藏排序',
  quietHoursEnabled: '免打扰时段',
  quietHoursStart: '免打扰开始',
  quietHoursEnd: '免打扰结束',
  notifyChat: '聊天消息提醒',
  notifySocial: '社交提醒',
  notifyMoments: '友链提醒',
  notifySystem: '系统提醒'
}

// debounce 800ms：避免快速连续点击导致请求风暴
const SAVE_DEBOUNCE_MS = 800

/** 内置可用的提示音列表（与 utils/notifyTone.ts 中的 toneId 对应） */
export const NOTIFY_TONES = ['default', 'chime', 'bell', 'pop'] as const
export type NotifyToneId = (typeof NOTIFY_TONES)[number]

function defaultState() {
  return {
    autoStart: false,
    soundNotify: true,
    messageDetail: true,
    notifyAtMe: true,
    notifySound: false,
    privacyVerifyFriend: true,
    privacyAllowStranger: false,
    privacyShowOnline: true,
    language: 'zh-CN' as string,
    chatBackground: 'default' as ChatBackgroundId,
    notifyTone: 'default' as NotifyToneId,
    momentsBackground: '' as string,
    /** 收藏视图：grid / list（云端同步） */
    favoritesViewMode: 'grid' as 'grid' | 'list',
    /** 收藏排序：newest / oldest / title（云端同步） */
    favoritesSort: 'newest' as 'newest' | 'oldest' | 'title',
    quietHoursEnabled: false,
    quietHoursStart: '22:00',
    quietHoursEnd: '08:00',
    notifyChat: true,
    notifySocial: true,
    notifyMoments: true,
    notifySystem: true,
    /** 关闭窗口时最小化到托盘（本地 + Electron 主进程） */
    minimizeToTray: true,
    /** 启动时打开主窗口或仅托盘（本地 + Electron 主进程） */
    openOnStartup: 'main' as 'main' | 'tray',
    /** 主题色预设 ID（仅本地） */
    accentColor: 'cyan' as string,
    /** 主题模式：跟随系统 / 浅色 / 深色（仅本地；实际明暗仍写入 app.theme） */
    themeMode: 'light' as 'system' | 'light' | 'dark',
    /** 文件下载目录（仅本地） */
    downloadPath: '' as string,
    /** 每次下载是否询问保存位置（仅本地） */
    downloadAskEveryTime: true,
    /** 显示/隐藏主窗口快捷键（仅本地） */
    shortcutToggleWindow: 'CommandOrControl+Shift+L',
    /** 锁定应用快捷键（仅本地） */
    shortcutLock: 'CommandOrControl+Shift+K',
    /** 标记已经从服务端成功拉取过，避免每次启动都先写空 patch 覆盖服务端真实值 */
    _hydrated: false,
    /** 标记是否处于登录态；离线时不向服务端写 */
    _online: false
  }
}

export const useAppSettingsStore = defineStore('appSettings', {
  state: defaultState,

  actions: {
    /**
     * 设置聊天背景
     * @param id 背景主题 ID
     */
    setChatBackground(id: ChatBackgroundId) {
      this.chatBackground = id
    },

    /** 设置提示音（音色） */
    setNotifyTone(id: NotifyToneId) {
      this.notifyTone = id
    },

    /**
     * 把桌面相关偏好同步到 Electron 主进程（关窗进托盘 / 启动打开方式 / 托盘语言）。
     * Web 环境下 no-op。
     */
    async syncDesktopPrefs() {
      if (!window.electronAPI?.setDesktopPrefs) return
      try {
        await window.electronAPI.setDesktopPrefs({
          minimizeToTray: this.minimizeToTray,
          openOnStartup: this.openOnStartup === 'tray' ? 'tray' : 'main',
          language: this.language === 'en-US' ? 'en-US' : 'zh-CN'
        })
      } catch (e) {
        console.warn('[appSettings] 同步桌面偏好失败:', e)
      }
    },

    /** 将所有设置恢复为默认值（保留 hydration 标记） */
    reset() {
      const hydrated = this._hydrated
      const online = this._online
      Object.assign(this, defaultState(), { _hydrated: hydrated, _online: online })
    },

    /** 标记进入登录态；订阅 debounce 保存 */
    markOnline() {
      this._online = true
    },

    /** 标记登出，停止保存，清空 hydration 标记（下次登录重新拉） */
    markOffline() {
      this._online = false
      this._hydrated = false
    },

    /**
     * 从服务端拉取偏好设置并覆盖本地（登录成功后调用一次）。
     * @returns 是否成功
     */
    async loadFromServer(): Promise<boolean> {
      try {
        const res = await preferenceApi.getPreference()
        if (res.code === 200 && res.data) {
          this.applyServerData(res.data)
          this._hydrated = true
          // 服务端语言覆盖后同步到界面与 Electron
          setLocale(this.language || 'zh-CN')
          void this.syncDesktopPrefs()
          return true
        }
        return false
      } catch (e) {
        console.warn('[appSettings] 加载服务端偏好失败，使用本地缓存:', e)
        return false
      }
    },

    /** 把服务端返回的对象合并到本地 state（仅覆盖已知字段） */
    applyServerData(data: Partial<preferenceApi.UserPreference>) {
      if (typeof data.autoStart === 'boolean') this.autoStart = data.autoStart
      if (typeof data.soundNotify === 'boolean') this.soundNotify = data.soundNotify
      if (typeof data.messageDetail === 'boolean') this.messageDetail = data.messageDetail
      if (typeof data.notifyAtMe === 'boolean') this.notifyAtMe = data.notifyAtMe
      if (typeof data.notifySound === 'boolean') this.notifySound = data.notifySound
      if (typeof data.privacyVerifyFriend === 'boolean') this.privacyVerifyFriend = data.privacyVerifyFriend
      if (typeof data.privacyAllowStranger === 'boolean') this.privacyAllowStranger = data.privacyAllowStranger
      if (typeof data.privacyShowOnline === 'boolean') this.privacyShowOnline = data.privacyShowOnline
      if (typeof data.language === 'string' && data.language) this.language = data.language
      if (typeof data.chatBackground === 'string' && data.chatBackground) this.chatBackground = data.chatBackground as ChatBackgroundId
      if (typeof (data as { notifyTone?: unknown }).notifyTone === 'string') {
        const tone = (data as { notifyTone: string }).notifyTone
        if ((NOTIFY_TONES as readonly string[]).includes(tone)) {
          this.notifyTone = tone as NotifyToneId
        }
      }
      const mb = (data as { momentsBackground?: unknown }).momentsBackground
      if (typeof mb === 'string' && mb) {
        this.momentsBackground = mb
      }
      const vm = (data as { favoritesViewMode?: unknown }).favoritesViewMode
      if (vm === 'grid' || vm === 'list') {
        this.favoritesViewMode = vm
      }
      const sk = (data as { favoritesSort?: unknown }).favoritesSort
      if (sk === 'newest' || sk === 'oldest' || sk === 'title') {
        this.favoritesSort = sk
      }
      if (typeof data.quietHoursEnabled === 'boolean') this.quietHoursEnabled = data.quietHoursEnabled
      if (typeof data.quietHoursStart === 'string' && data.quietHoursStart) this.quietHoursStart = data.quietHoursStart
      if (typeof data.quietHoursEnd === 'string' && data.quietHoursEnd) this.quietHoursEnd = data.quietHoursEnd
      if (typeof data.notifyChat === 'boolean') this.notifyChat = data.notifyChat
      if (typeof data.notifySocial === 'boolean') this.notifySocial = data.notifySocial
      if (typeof data.notifyMoments === 'boolean') this.notifyMoments = data.notifyMoments
      if (typeof data.notifySystem === 'boolean') this.notifySystem = data.notifySystem
    },

    /**
     * 把单个字段变更合并到"待保存 payload"（仅记录变更字段以最小化请求体）。
     * 触发防抖保存；保存结果通过 preferenceEvents 广播（成功/失败 toast 用）。
     */
    scheduleSave(key: SyncableKey) {
      if (!this._online) return
      if (!SYNCABLE_KEYS.includes(key)) return
      const value = this[key]
      if (value === undefined || value === null) return
      // 此时 value 已被收窄为 boolean | string，与 UserPreferencePatch[key] 一致
      ;(pendingPatch as Record<string, boolean | string>)[key] = value
      scheduleFlush(this)
    },

    /** 强制立刻 flush（被 app.logout 调用，避免 pending 请求撞上 token 失效） */
    flushPendingSave() {
      if (Object.keys(pendingPatch).length === 0) return
      if (saveTimer != null) {
        clearTimeout(saveTimer)
        saveTimer = null
      }
      void doFlush(this)
    }
  },

  // 持久化全部可配置字段（含 _hydrated/_online 标记，前端离线缓存）
  persist: {
    key: 'linkx-settings',
    paths: [
      'autoStart',
      'soundNotify',
      'messageDetail',
      'notifyAtMe',
      'notifySound',
      'privacyVerifyFriend',
      'privacyAllowStranger',
      'privacyShowOnline',
      'language',
      'chatBackground',
      'notifyTone',
      'momentsBackground',
      'favoritesViewMode',
      'favoritesSort',
      'quietHoursEnabled',
      'quietHoursStart',
      'quietHoursEnd',
      'notifyChat',
      'notifySocial',
      'notifyMoments',
      'notifySystem',
      'minimizeToTray',
      'openOnStartup',
      'accentColor',
      'themeMode',
      'downloadPath',
      'downloadAskEveryTime',
      'shortcutToggleWindow',
      'shortcutLock',
      '_hydrated',
      '_online'
    ]
  }
})

// ============================================================
// 模块级 debounce flush（多组件共享同一队列，避免重复请求）
// ============================================================

const pendingPatch: preferenceApi.UserPreferencePatch = {}
let saveTimer: ReturnType<typeof setTimeout> | null = null
let flushing = false

function scheduleFlush(store: ReturnType<typeof useAppSettingsStore>) {
  if (saveTimer != null) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
    saveTimer = null
    void doFlush(store)
  }, SAVE_DEBOUNCE_MS)
}

async function doFlush(store: ReturnType<typeof useAppSettingsStore>) {
  if (flushing) {
    scheduleFlush(store)
    return
  }
  if (Object.keys(pendingPatch).length === 0) return

  // 拷贝并清空待保存项；flush 中途若再有新变更会落到新一轮 flush
  const payload: preferenceApi.UserPreferencePatch = { ...pendingPatch }
  for (const k of Object.keys(pendingPatch)) {
    delete (pendingPatch as Record<string, unknown>)[k]
  }
  void store
  flushing = true
  try {
    await preferenceApi.updatePreference(payload)
    // 成功：广播 success 事件，附上字段→值映射供组件拼 toast 文案
    onPreferenceChange.emit({
      kind: 'success',
      fields: payload,
      fieldLabel: payload ? FIELD_LABEL : FIELD_LABEL
    })
  } catch (e) {
    // 失败：广播 error 事件（错误原因附在 message）
    onPreferenceChange.emit({
      kind: 'error',
      fields: payload,
      message: e instanceof Error ? e.message : '保存失败'
    })
    console.warn('[appSettings] 保存偏好失败:', e)
  } finally {
    flushing = false
  }
}