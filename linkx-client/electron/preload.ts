/**
 * 作者：yangleduo
 */
import { contextBridge, ipcRenderer } from 'electron'

type WinAction = 'minimize' | 'maximize' | 'close'

const channels = {
  minimize: 'window-minimize',
  maximize: 'window-maximize',
  close: 'window-close'
} as const

const invokeChannels = {
  minimize: 'window:minimize',
  maximize: 'window:maximize',
  close: 'window:close'
} as const

function windowAction(action: WinAction) {
  ipcRenderer.send(channels[action])
  void ipcRenderer.invoke(invokeChannels[action]).catch(() => {
    /* send 已处理时 invoke 可忽略 */
  })
}

// [P2-E3] 屏幕截图 API：改为通过 IPC 调用主进程，由主进程弹窗确认后再执行截图，
// 避免渲染进程直接调用 desktopCapturer 静默截屏
async function captureScreen(): Promise<{ dataURL: string; width: number; height: number } | null> {
  try {
    return await ipcRenderer.invoke('screen:capture')
  } catch (e) {
    console.error('screenshot failed:', e)
    return null
  }
}

// 通过 IPC 调用主进程 IP 定位（主进程可访问 http/https）
function fetchIPLocation(): Promise<string | null> {
  return ipcRenderer.invoke('fetch-ip-location')
}

const api = {
  minimize: () => windowAction('minimize'),
  maximize: () => windowAction('maximize'),
  close: () => windowAction('close'),
  isMaximized: () => ipcRenderer.invoke('window:is-maximized') as Promise<boolean>,
  onMaximizedChange: (callback: (maximized: boolean) => void) => {
    if (typeof callback !== 'function') return () => {}
    const channel = 'window-maximized-changed'
    const listener = (_event: Electron.IpcRendererEvent, maximized: boolean) =>
      callback(!!maximized)
    ipcRenderer.on(channel, listener)
    return () => ipcRenderer.removeListener(channel, listener)
  },
  isElectron: true as const,
  showCustomCaptionButtons: process.platform === 'win32' || process.platform === 'linux',
  hasNativeTitleBarOverlay: false,
  openMoments: (opts?: { userId?: string; name?: string }) =>
    ipcRenderer.send('window-open-moments', opts || {}),
  openMomentsText: () => ipcRenderer.send('window-open-moments-text'),
  openMomentsMedia: () => ipcRenderer.send('window-open-moments-media'),
  openNoteEditor: () => ipcRenderer.send('window-open-note-editor'),
  onNoteEditorReset: (callback: () => void) => {
    if (typeof callback !== 'function') return () => {}
    const listener = () => callback()
    ipcRenderer.on('note-editor:reset', listener)
    return () => ipcRenderer.removeListener('note-editor:reset', listener)
  },
  openRegister: () => ipcRenderer.send('window-open-register'),
  openHelp: () => ipcRenderer.send('window-open-help'),
  openChatHistory: () => ipcRenderer.send('window-open-chat-history'),
  openOfficialNotifyDetail: (notifId: string) =>
    ipcRenderer.send('window-open-official-notify-detail', notifId),
  openImageViewer: (payload: {
    url: string
    fileName?: string
    fileSize?: string
    items?: Array<{ url: string; fileName?: string; fileSize?: string }>
    index?: number
  }) => ipcRenderer.send('window-open-image-viewer', payload || {}),
  getImageViewerPayload: () =>
    ipcRenderer.invoke('image-viewer:get-payload') as Promise<{
      url?: string
      fileName?: string
      fileSize?: string
      items?: Array<{ url: string; fileName?: string; fileSize?: string }>
      index?: number
    } | null>,
  onImageViewerPayload: (
    callback: (data: {
      url?: string
      fileName?: string
      fileSize?: string
      items?: Array<{ url: string; fileName?: string; fileSize?: string }>
      index?: number
    }) => void
  ) => {
    if (typeof callback !== 'function') return () => {}
    const listener = (
      _event: Electron.IpcRendererEvent,
      data: {
        url?: string
        fileName?: string
        fileSize?: string
        items?: Array<{ url: string; fileName?: string; fileSize?: string }>
        index?: number
      }
    ) => callback(data || {})
    ipcRenderer.on('image-viewer:payload', listener)
    return () => ipcRenderer.removeListener('image-viewer:payload', listener)
  },
  captureScreen,
  fetchIPLocation,
  notifyMomentsPublished: () => ipcRenderer.send('moments:published'),
  onMomentsRefresh: (callback: () => void) => {
    if (typeof callback !== 'function') return () => {}
    const listener = () => callback()
    ipcRenderer.on('moments:refresh', listener)
    return () => ipcRenderer.removeListener('moments:refresh', listener)
  },
  showNotification: (payload: { title?: string; body?: string; silent?: boolean }) =>
    ipcRenderer.invoke('app:show-notification', payload) as Promise<boolean>,
  onInAppToast: (callback: (data: { title?: string; body?: string }) => void) => {
    if (typeof callback !== 'function') return () => {}
    const listener = (_event: Electron.IpcRendererEvent, data: { title?: string; body?: string }) =>
      callback(data || {})
    ipcRenderer.on('app:in-app-toast', listener)
    return () => ipcRenderer.removeListener('app:in-app-toast', listener)
  },
  secureStorage: {
    isAvailable: () => ipcRenderer.invoke('secure-storage:is-available'),
    get: (key: string) => ipcRenderer.invoke('secure-storage:get', key),
    set: (key: string, value: string) => ipcRenderer.invoke('secure-storage:set', key, value),
    remove: (key: string) => ipcRenderer.invoke('secure-storage:remove', key)
  },
  // 设置开机自启（由主进程 app.setLoginItemSettings 实现）
  setAutoStart: (enabled: boolean) => ipcRenderer.invoke('app:set-auto-start', enabled),
  getAutoStart: () => ipcRenderer.invoke('app:get-auto-start') as Promise<boolean>,
  getDesktopPrefs: () =>
    ipcRenderer.invoke('app:get-desktop-prefs') as Promise<{
      minimizeToTray: boolean
      openOnStartup: 'main' | 'tray'
      language: 'zh-CN' | 'en-US'
    }>,
  setDesktopPrefs: (prefs: {
    minimizeToTray?: boolean
    openOnStartup?: 'main' | 'tray'
    language?: 'zh-CN' | 'en-US'
  }) =>
    ipcRenderer.invoke('app:set-desktop-prefs', prefs) as Promise<{
      minimizeToTray: boolean
      openOnStartup: 'main' | 'tray'
      language: 'zh-CN' | 'en-US'
    }>,
  setWindowMode: mode => ipcRenderer.invoke('window:set-mode', mode),
  pickDownloadPath: () => ipcRenderer.invoke('app:pick-download-path'),
  pickImages: () =>
    ipcRenderer.invoke('app:pick-images') as Promise<
      Array<{ name: string; mimeType: string; data: ArrayBuffer | Uint8Array }>
    >,
  openDownloadPath: customPath => ipcRenderer.invoke('app:open-download-path', customPath),
  clearAppCache: () => ipcRenderer.invoke('app:clear-cache'),
  getDownloadPath: () => ipcRenderer.invoke('app:get-download-path'),
  downloadFile: payload => ipcRenderer.invoke('app:download-file', payload),
  downloadAndInstallUpdate: payload =>
    ipcRenderer.invoke('app:download-and-install-update', payload),
  onUpdateProgress: (callback: (data: { phase?: string; percent?: number }) => void) => {
    if (typeof callback !== 'function') return () => {}
    const listener = (
      _event: Electron.IpcRendererEvent,
      data: { phase?: string; percent?: number }
    ) => callback(data || {})
    ipcRenderer.on('app:update-progress', listener)
    return () => ipcRenderer.removeListener('app:update-progress', listener)
  },
  // 主题变更通知（与主进程的 theme-changed 通道对应）
  notifyThemeChange: (theme: 'light' | 'dark') => ipcRenderer.send('theme-changed', theme),
  /** 主进程剪贴板写入（避免 Chromium clipboard 权限限制） */
  clipboardWriteText: (text: string) =>
    ipcRenderer.invoke('clipboard:write-text', text) as Promise<boolean>,
  clipboardWriteImage: (payload: { dataUrl?: string; url?: string }) =>
    ipcRenderer.invoke('clipboard:write-image', payload || {}) as Promise<boolean>,
  openExternal: (url: string) => ipcRenderer.invoke('shell:open-external', url) as Promise<boolean>,
  openPath: (filePath: string) => ipcRenderer.invoke('shell:open-path', filePath) as Promise<boolean>,
  getPlatform: (): 'windows' | 'macos' | 'linux' => {
    if (process.platform === 'win32') return 'windows'
    if (process.platform === 'darwin') return 'macos'
    return 'linux'
  },
  /** 渲染进程首屏就绪后通知主进程展示窗口（避免空白灰窗） */
  notifyWindowReady: () => ipcRenderer.send('window:content-ready')
}

contextBridge.exposeInMainWorld('electronAPI', api)