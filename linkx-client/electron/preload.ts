import { contextBridge, ipcRenderer, desktopCapturer } from 'electron'

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

// 屏幕截图 API
async function captureScreen(): Promise<{ dataURL: string; width: number; height: number } | null> {
  try {
    const sources = await desktopCapturer.getSources({
      types: ['screen'],
      thumbnailSize: { width: 1920, height: 1080 }
    })
    if (sources.length === 0) return null
    const source = sources[0]
    return {
      dataURL: source.thumbnail.toDataURL(),
      width: source.thumbnail.getSize().width,
      height: source.thumbnail.getSize().height
    }
  } catch (e) {
    console.error('截图失败:', e)
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
  isElectron: true as const,
  hasNativeTitleBarOverlay: process.platform === 'win32' || process.platform === 'linux',
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
  notifyThemeChange: (theme: 'light' | 'dark') => ipcRenderer.send('theme-changed', theme)
}

contextBridge.exposeInMainWorld('electronAPI', api)