const { contextBridge, ipcRenderer, desktopCapturer } = require('electron')

const MAX_CHANGED = 'window-maximized-changed'

// 屏幕截图 API
async function captureScreen() {
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

// 通过 IPC 调用主进程的 IP 定位（主进程可访问 http/https 模块）
async function fetchIPLocation() {
  return ipcRenderer.invoke('fetch-ip-location')
}

contextBridge.exposeInMainWorld('electronAPI', {
  minimize: () => ipcRenderer.invoke('window:minimize'),
  maximize: () => ipcRenderer.invoke('window:maximize'),
  close: () => ipcRenderer.invoke('window:close'),
  openMoments: () => ipcRenderer.send('window-open-moments'),
  openMomentsText: () => ipcRenderer.send('window-open-moments-text'),
  openMomentsMedia: () => ipcRenderer.send('window-open-moments-media'),
  openNoteEditor: () => ipcRenderer.send('window-open-note-editor'),
  openRegister: () => ipcRenderer.send('window-open-register'),
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
  setWindowMode: mode => ipcRenderer.invoke('window:set-mode', mode),
  pickDownloadPath: () => ipcRenderer.invoke('app:pick-download-path'),
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
  /** Windows/Linux：使用系统原生标题栏按钮（titleBarOverlay） */
  hasNativeTitleBarOverlay: process.platform === 'win32' || process.platform === 'linux',
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
  }
})
