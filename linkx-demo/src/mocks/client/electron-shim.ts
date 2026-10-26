/**
 * 作者：yangleduo
 */
const secureStore = new Map<string, string>()

type ChatDbRow = { id: string; payload: string; createTime: number }
type ChatDbMeta = { sessionId: string; scrollTop: number; lastSyncId: string | null; updatedAt: number }

function createMemoryChatDb() {
  const messages = new Map<string, ChatDbRow[]>()
  const meta = new Map<string, ChatDbMeta>()

  function rowsOf(sessionId: string): ChatDbRow[] {
    if (!messages.has(sessionId)) messages.set(sessionId, [])
    return messages.get(sessionId)!
  }

  function metaOf(sessionId: string): ChatDbMeta {
    const existing = meta.get(sessionId)
    if (existing) return existing
    const next = { sessionId, scrollTop: 0, lastSyncId: null as string | null, updatedAt: Date.now() }
    meta.set(sessionId, next)
    return next
  }

  return {
    upsertMessages: async (sessionId: string, rows: Array<{ id: string; payload: string; createTime?: number }>) => {
      const list = rowsOf(sessionId)
      for (const row of rows) {
        const item = { id: row.id, payload: row.payload, createTime: row.createTime ?? 0 }
        const idx = list.findIndex(r => r.id === item.id)
        if (idx >= 0) list[idx] = item
        else list.push(item)
      }
      list.sort((a, b) => a.createTime - b.createTime)
      return true
    },
    getRecent: async (sessionId: string, limit = 200) => {
      const list = rowsOf(sessionId)
      return list.slice(-limit).map(r => r.payload)
    },
    getBefore: async (sessionId: string, beforeId: string, limit = 50) => {
      const list = rowsOf(sessionId)
      const idx = list.findIndex(r => r.id === beforeId)
      const end = idx < 0 ? list.length : idx
      return list.slice(Math.max(0, end - limit), end).map(r => r.payload)
    },
    getLastId: async (sessionId: string) => {
      const list = rowsOf(sessionId)
      return list.length ? list[list.length - 1].id : null
    },
    count: async (sessionId?: string) => {
      if (sessionId) return rowsOf(sessionId).length
      let n = 0
      for (const list of messages.values()) n += list.length
      return n
    },
    getPath: async () => ':memory:demo',
    hasOlder: async (sessionId: string, oldestId: string) => {
      const list = rowsOf(sessionId)
      const idx = list.findIndex(r => r.id === oldestId)
      return idx > 0
    },
    getSessionMeta: async (sessionId: string) => ({ ...metaOf(sessionId) }),
    setSessionMeta: async (sessionId: string, patch: { scrollTop?: number; lastSyncId?: string | null }) => {
      const current = metaOf(sessionId)
      if (patch.scrollTop != null) current.scrollTop = patch.scrollTop
      if (patch.lastSyncId !== undefined) current.lastSyncId = patch.lastSyncId
      current.updatedAt = Date.now()
      return true
    },
    clearSession: async (sessionId: string) => {
      messages.delete(sessionId)
      meta.delete(sessionId)
      return true
    },
    clearAll: async () => {
      messages.clear()
      meta.clear()
      return true
    },
    migrateLegacy: async (map: Record<string, Array<Record<string, unknown>>>) => {
      let count = 0
      for (const [sessionId, list] of Object.entries(map)) {
        const rows = (list || []).map(msg => ({
          id: String(msg.id || ''),
          payload: JSON.stringify(msg),
          createTime: Number(msg.createTime || 0),
        })).filter(r => r.id)
        if (!rows.length) continue
        const store = rowsOf(sessionId)
        store.push(...rows)
        count += rows.length
      }
      return count
    },
  }
}

const IMAGE_VIEWER_KEY = 'lx-demo-image-viewer'

type ImageViewerPayload = {
  url?: string
  fileName?: string
  fileSize?: string
  items?: Array<{
    url: string
    fileName?: string
    fileSize?: string
    messageId?: string
    conversationId?: string
  }>
  index?: number
}

function navHash(path: string) {
  const next = path.startsWith('/') ? path : `/${path}`
  window.location.hash = next
}

function readImageViewerPayload(): ImageViewerPayload | null {
  try {
    const raw = sessionStorage.getItem(IMAGE_VIEWER_KEY)
    return raw ? (JSON.parse(raw) as ImageViewerPayload) : null
  } catch {
    return null
  }
}

function closeDemoWindow() {
  const hash = window.location.hash.replace(/^#/, '')
  if (!hash || hash === '/') return
  if (window.history.length > 1) {
    window.history.back()
    return
  }
  navHash('/')
}

export function installClientElectronShim() {
  if (window.electronAPI?.isElectron) {
    window.electronAPI.close = async () => {
      closeDemoWindow()
    }
    return
  }

  const noopAsync = async () => undefined
  const imageViewerListeners = new Set<(data: ImageViewerPayload) => void>()
  let imageViewerPayload = readImageViewerPayload()

  window.electronAPI = {
    isElectron: true,
    showCustomCaptionButtons: true,
    hasNativeTitleBarOverlay: false,
    useNativeWindowFrame: false,
    minimize: noopAsync,
    maximize: noopAsync,
    close: async () => {
      closeDemoWindow()
    },
    isMaximized: async () => false,
    isPinned: async () => false,
    togglePin: async () => false,
    onMaximizedChange: () => () => {},
    openMoments: () => navHash('/moments'),
    openShortVideo: () => navHash('/short-video'),
    openMomentsText: () => navHash('/moments/text'),
    openMomentsMedia: () => navHash('/moments/media'),
    openNotes: noteId => navHash(noteId ? `/notes/${noteId}` : '/notes'),
    openNoteEditor: () => navHash('/notes'),
    openRegister: () => navHash('/register'),
    openChatHistory: () => navHash('/chat-history'),
    openLinkMate: sessionId => navHash(sessionId ? `/linkmate/${sessionId}` : '/linkmate'),
    openOfficialNotifyDetail: notifId => navHash(`/official-notify/${encodeURIComponent(notifId)}`),
    openImageViewer: payload => {
      imageViewerPayload = payload || null
      try {
        if (payload) sessionStorage.setItem(IMAGE_VIEWER_KEY, JSON.stringify(payload))
        else sessionStorage.removeItem(IMAGE_VIEWER_KEY)
      } catch {
        /* ignore quota */
      }
      navHash('/image-viewer')
      imageViewerListeners.forEach(cb => cb(payload || {}))
    },
    getImageViewerPayload: async () => imageViewerPayload ?? readImageViewerPayload(),
    onImageViewerPayload: callback => {
      imageViewerListeners.add(callback)
      return () => imageViewerListeners.delete(callback)
    },
    getPlatform: () => 'windows',
    setAutoStart: async () => true,
    getAutoStart: async () => false,
    getDesktopPrefs: async () => ({
      minimizeToTray: true,
      openOnStartup: 'main',
      language: 'zh-CN',
    }),
    setDesktopPrefs: async prefs => ({
      minimizeToTray: prefs.minimizeToTray ?? true,
      openOnStartup: prefs.openOnStartup ?? 'main',
      language: prefs.language ?? 'zh-CN',
    }),
    notifyThemeChange: () => {},
    setTitleBarOverlay: async () => true,
    setWindowMode: noopAsync,
    getWindowBounds: async () => null,
    setWindowBounds: async () => false,
    pickDownloadPath: async () => null,
    pickImages: async () => [],
    openDownloadPath: async () => false,
    clearAppCache: async () => ({ ok: true }),
    getDownloadPath: async () => '',
    downloadFile: async () => ({ ok: true, canceled: true }),
    downloadAndInstallUpdate: async () => ({ ok: false, message: '演示环境不支持更新' }),
    showNotification: noopAsync,
    openExternal: async (url: string) => {
      window.open(url, '_blank', 'noopener,noreferrer')
      return true
    },
    captureScreen: async () => null,
    secureStorage: {
      isAvailable: async () => true,
      get: async (key: string) => secureStore.get(key) ?? null,
      set: async (key: string, value: string) => {
        secureStore.set(key, value)
      },
      remove: async (key: string) => {
        secureStore.delete(key)
      },
    },
    chatDb: createMemoryChatDb(),
    chatMedia: {
      getPath: async () => null,
      saveBytes: async () => null,
    },
    onMomentsRefresh: () => () => {},
    onUpdateProgress: () => () => {},
  }

  document.documentElement.classList.add('lx-electron', 'lx-electron-win32')

  if (import.meta.env.VITE_LINKX_DEMO === '1') {
    secureStore.set('accessToken', 'demo-client-access')
    secureStore.set('refreshToken', 'demo-client-refresh')
  }
}
