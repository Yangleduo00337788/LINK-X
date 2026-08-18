/**
 * 作者：yangleduo
 */
const { contextBridge, ipcRenderer } = require('electron')

const MAX_CHANGED = 'window-maximized-changed'

// [P2-E3] 屏幕截图 API：改为通过 IPC 调用主进程，由主进程弹窗确认后再执行截图，
// 避免渲染进程直接调用 desktopCapturer 静默截屏
async function captureScreen() {
  try {
    return await ipcRenderer.invoke('screen:capture')
  } catch (e) {
    console.error('screenshot failed:', e)
    return null
  }
}

// 通过 IPC 调用主进程的 IP 定位（主进程可访问 http/https 模块）
async function fetchIPLocation() {
  return ipcRenderer.invoke('fetch-ip-location')
}

contextBridge.exposeInMainWorld('electronAPI', {
  minimize: () => ipcRenderer.invoke('window:minimize'),
  maximize: () => ipcRenderer.invoke('window:maximize'),
  close: () => ipcRenderer.invoke('window:close'),
  openMoments: (opts) => ipcRenderer.send('window-open-moments', opts || {}),
  openMomentsText: () => ipcRenderer.send('window-open-moments-text'),
  openMomentsMedia: () => ipcRenderer.send('window-open-moments-media'),
  openNotes: (noteId) =>
    ipcRenderer.send('window-open-notes', noteId ? { noteId: String(noteId) } : {}),
  openNoteEditor: () => ipcRenderer.send('window-open-notes'),
  openRegister: () => ipcRenderer.send('window-open-register'),
  openHelp: () => ipcRenderer.send('window-open-help'),
  openChatHistory: () => ipcRenderer.send('window-open-chat-history'),
  openLinkMate: sessionId => ipcRenderer.send('window-open-linkmate', sessionId || ''),
  openOfficialNotifyDetail: (notifId) =>
    ipcRenderer.send('window-open-official-notify-detail', notifId),
  /** 打开图片预览独立窗口（深色查看器） */
  openImageViewer: payload => ipcRenderer.send('window-open-image-viewer', payload || {}),
  getImageViewerPayload: () => ipcRenderer.invoke('image-viewer:get-payload'),
  onImageViewerPayload: callback => {
    if (typeof callback !== 'function') return () => {}
    const listener = (_event, data) => callback(data || {})
    ipcRenderer.on('image-viewer:payload', listener)
    return () => ipcRenderer.removeListener('image-viewer:payload', listener)
  },
  isMaximized: () => ipcRenderer.invoke('window:is-maximized'),
  isPinned: () => ipcRenderer.invoke('window:is-pinned'),
  togglePin: () => ipcRenderer.invoke('window:toggle-pin'),
  onMaximizedChange: callback => {
    if (typeof callback !== 'function') return () => {}
    const listener = (_event, maximized) => callback(!!maximized)
    ipcRenderer.on(MAX_CHANGED, listener)
    return () => ipcRenderer.removeListener(MAX_CHANGED, listener)
  },
  setAutoStart: enabled => ipcRenderer.invoke('app:set-auto-start', enabled),
  getAutoStart: () => ipcRenderer.invoke('app:get-auto-start'),
  getDesktopPrefs: () => ipcRenderer.invoke('app:get-desktop-prefs'),
  setDesktopPrefs: prefs => ipcRenderer.invoke('app:set-desktop-prefs', prefs),
  notifyThemeChange: theme => ipcRenderer.send('theme-changed', theme),
  setTitleBarOverlay: opts => ipcRenderer.invoke('window:set-titlebar-overlay', opts || {}),
  setWindowMode: mode => ipcRenderer.invoke('window:set-mode', mode),
  getWindowBounds: () => ipcRenderer.invoke('window:get-bounds'),
  setWindowBounds: bounds => ipcRenderer.invoke('window:set-bounds', bounds),
  pickDownloadPath: () => ipcRenderer.invoke('app:pick-download-path'),
  pickImages: () => ipcRenderer.invoke('app:pick-images'),
  openDownloadPath: customPath => ipcRenderer.invoke('app:open-download-path', customPath),
  clearAppCache: () => ipcRenderer.invoke('app:clear-cache'),
  getDownloadPath: () => ipcRenderer.invoke('app:get-download-path'),
  downloadFile: payload => ipcRenderer.invoke('app:download-file', payload),
  downloadAndInstallUpdate: payload =>
    ipcRenderer.invoke('app:download-and-install-update', payload),
  onUpdateProgress: callback => {
    if (typeof callback !== 'function') return () => {}
    const listener = (_event, data) => callback(data || {})
    ipcRenderer.on('app:update-progress', listener)
    return () => ipcRenderer.removeListener('app:update-progress', listener)
  },
  getShortcuts: () => ipcRenderer.invoke('app:get-shortcuts'),
  setShortcuts: payload => ipcRenderer.invoke('app:set-shortcuts', payload),
  onShortcutLock: callback => {
    if (typeof callback !== 'function') return () => {}
    const listener = () => callback()
    ipcRenderer.on('app:shortcut-lock', listener)
    return () => ipcRenderer.removeListener('app:shortcut-lock', listener)
  },
  isElectron: true,
  /** 使用自绘窗控（macOS/Linux 无边框窗）；Win32 全窗原生边框 */
  showCustomCaptionButtons: process.platform === 'linux',
  /** Win32 登录窗自绘顶栏；主界面/子窗口为系统原生边框 */
  useNativeWindowFrame: process.platform === 'win32',
  hasNativeTitleBarOverlay: process.platform === 'win32',
  captureScreen,
  fetchIPLocation,
  /** 发布成功后通知友链列表窗口刷新 */
  notifyMomentsPublished: () => ipcRenderer.send('moments:published'),
  /** 订阅友链列表刷新（发布成功后触发），返回取消订阅函数 */
  onMomentsRefresh: callback => {
    if (typeof callback !== 'function') return () => {}
    const listener = () => callback()
    ipcRenderer.on('moments:refresh', listener)
    return () => ipcRenderer.removeListener('moments:refresh', listener)
  },
  /** 弹出系统桌面通知 */
  showNotification: (payload) => ipcRenderer.invoke('app:show-notification', payload),
  showMainWindow: () => ipcRenderer.invoke('window:show-main'),
  setTaskbarBadge: count => ipcRenderer.invoke('window:set-taskbar-badge', count),
  setWindowProgress: progress => ipcRenderer.invoke('window:set-progress-bar', progress),
  flashWindow: flash => ipcRenderer.invoke('window:flash-frame', flash),
  syncCallToolbar: payload => ipcRenderer.invoke('window:sync-call-toolbar', payload || {}),
  onNotificationAction: callback => {
    if (typeof callback !== 'function') return () => {}
    const listener = (_event, data) => callback(data || {})
    ipcRenderer.on('app:notification-action', listener)
    return () => ipcRenderer.removeListener('app:notification-action', listener)
  },
  onJumpListAction: callback => {
    if (typeof callback !== 'function') return () => {}
    const listener = (_event, action) => callback(String(action || ''))
    ipcRenderer.on('app:jump-list-action', listener)
    return () => ipcRenderer.removeListener('app:jump-list-action', listener)
  },
  getTrayMessagePayload: () => ipcRenderer.invoke('tray-message:get-payload'),
  openTrayMessage: () => ipcRenderer.invoke('tray-message:open'),
  ignoreTrayMessages: () => ipcRenderer.invoke('tray-message:ignore-all'),
  setTrayPopupHover: hovering => ipcRenderer.invoke('tray-message:popup-hover', hovering),
  onTrayMessageData: callback => {
    if (typeof callback !== 'function') return () => {}
    const listener = (_event, data) => callback(data || {})
    ipcRenderer.on('tray-message:data', listener)
    return () => ipcRenderer.removeListener('tray-message:data', listener)
  },
  onCallToolbarAction: callback => {
    if (typeof callback !== 'function') return () => {}
    const listener = (_event, action) => callback(action)
    ipcRenderer.on('app:call-toolbar-action', listener)
    return () => ipcRenderer.removeListener('app:call-toolbar-action', listener)
  },
  /** 订阅应用内 toast（主进程桌面通知失败时的兜底） */
  onInAppToast: callback => {
    if (typeof callback !== 'function') return () => {}
    const listener = (_event, data) => callback(data || {})
    ipcRenderer.on('app:in-app-toast', listener)
    return () => ipcRenderer.removeListener('app:in-app-toast', listener)
  },
  secureStorage: {
    isAvailable: () => ipcRenderer.invoke('secure-storage:is-available'),
    get: key => ipcRenderer.invoke('secure-storage:get', key),
    set: (key, value) => ipcRenderer.invoke('secure-storage:set', key, value),
    remove: key => ipcRenderer.invoke('secure-storage:remove', key)
  },
  /** 主进程剪贴板写入（避免 Chromium clipboard 权限限制） */
  clipboardWriteText: text => ipcRenderer.invoke('clipboard:write-text', text),
  clipboardWriteImage: payload => ipcRenderer.invoke('clipboard:write-image', payload || {}),
  openExternal: url => ipcRenderer.invoke('shell:open-external', url),
  openPath: filePath => ipcRenderer.invoke('shell:open-path', filePath),
  getPlatform: () => {
    if (process.platform === 'win32') return 'windows'
    if (process.platform === 'darwin') return 'macos'
    return 'linux'
  },
  /** 渲染进程首屏绘制完成，通知主进程展示窗口 */
  notifyWindowReady: () => ipcRenderer.send('window:content-ready'),
  chatDb: {
    upsertMessages: (sessionId, rows) => ipcRenderer.invoke('chat-db:upsert-messages', sessionId, rows),
    getRecent: (sessionId, limit) => ipcRenderer.invoke('chat-db:get-recent', sessionId, limit),
    getBefore: (sessionId, beforeId, limit) => ipcRenderer.invoke('chat-db:get-before', sessionId, beforeId, limit),
    getLastId: sessionId => ipcRenderer.invoke('chat-db:get-last-id', sessionId),
    count: sessionId => ipcRenderer.invoke('chat-db:count', sessionId),
    getPath: () => ipcRenderer.invoke('chat-db:get-path'),
    hasOlder: (sessionId, oldestId) => ipcRenderer.invoke('chat-db:has-older', sessionId, oldestId),
    getSessionMeta: sessionId => ipcRenderer.invoke('chat-db:get-session-meta', sessionId),
    setSessionMeta: (sessionId, patch) => ipcRenderer.invoke('chat-db:set-session-meta', sessionId, patch),
    clearSession: sessionId => ipcRenderer.invoke('chat-db:clear-session', sessionId),
    clearAll: () => ipcRenderer.invoke('chat-db:clear-all'),
    migrateLegacy: map => ipcRenderer.invoke('chat-db:migrate-legacy', map)
  },
  chatMedia: {
    getPath: (messageId, kind) => ipcRenderer.invoke('chat-media:get-path', messageId, kind),
    saveBytes: payload => ipcRenderer.invoke('chat-media:save-bytes', payload)
  }
})
