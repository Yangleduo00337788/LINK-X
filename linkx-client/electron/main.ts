import { app, BrowserWindow, ipcMain, Tray, Menu, nativeImage, globalShortcut, safeStorage, desktopCapturer, type IpcMainEvent, type IpcMainInvokeEvent, type WebRequestHeadersReceivedCallbackParams, type OnHeadersReceivedListener } from 'electron'
import path from 'node:path'
import fs from 'node:fs'
import http from 'node:http'
import https from 'node:https'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const isDev = Boolean(process.env.VITE_DEV_SERVER_URL)

const SECURE_DIR = () => path.join(app.getPath('userData'), 'secure')

// 允许的 key 白名单，防止路径穿越攻击
const ALLOWED_KEY_PATTERN = /^[a-zA-Z][a-zA-Z0-9_-]{0,31}$/

function isValidKey(key: string): boolean {
  return ALLOWED_KEY_PATTERN.test(key)
}

function ensureSecureDir() {
  const dir = SECURE_DIR()
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true })
  }
  return dir
}

function secureFilePath(key: string) {
  return path.join(ensureSecureDir(), `${key}.bin`)
}

/** 使用原生 CJS preload，避免 Vite 打出 ESM 导致 Windows 上无法注入 */
function resolvePreloadPath(): string {
  const candidates = [
    path.join(__dirname, '../preload/preload.cjs'),
    path.join(__dirname, '../../electron/preload.cjs'),
    path.join(__dirname, '../../../electron/preload.cjs')
  ]
  for (const p of candidates) {
    const abs = path.resolve(p)
    if (fs.existsSync(abs)) return abs
  }
  console.error('[electron] preload not found, tried:', candidates)
  return path.resolve(candidates[0])
}

const preloadPath = resolvePreloadPath()

if (isDev) {
  app.commandLine.appendSwitch('--disable-software-rasterizer')
}

// 启用地理位置支持
app.commandLine.appendSwitch('--enable-geolocation')
app.commandLine.appendSwitch('--use-fake-ui-for-media-stream')
app.commandLine.appendSwitch('--use-fake-device-for-media-stream')

let mainWindow: BrowserWindow | null = null
let tray: Tray | null = null

/** 英文省市名 → 中文（ipinfo 等海外接口） */
const CITY_CN: Record<string, string> = {
  Beijing: '北京', Shanghai: '上海', Guangzhou: '广州', Shenzhen: '深圳',
  Chengdu: '成都', Hangzhou: '杭州', Wuhan: '武汉', Xian: '西安', XiAn: '西安',
  Nanjing: '南京', Chongqing: '重庆', Tianjin: '天津', Suzhou: '苏州',
  Dalian: '大连', Qingdao: '青岛', Ningbo: '宁波', Fuzhou: '福州',
  Xiamen: '厦门', Changsha: '长沙', Zhengzhou: '郑州', Kunming: '昆明',
  Guiyang: '贵阳', Urumqi: '乌鲁木齐', Lhasa: '拉萨', Xining: '西宁',
  Yinchuan: '银川', Haikou: '海口', Sanya: '三亚', Shenyang: '沈阳',
  Changchun: '长春', Harbin: '哈尔滨', Shijiazhuang: '石家庄', Taiyuan: '太原',
  Hohhot: '呼和浩特', Nanchang: '南昌', Nanning: '南宁', Lanzhou: '兰州',
  Gansu: '甘肃', Zhejiang: '浙江', Guangdong: '广东', Jiangsu: '江苏',
  Shandong: '山东', Sichuan: '四川', Hubei: '湖北', Hunan: '湖南',
  Henan: '河南', Hebei: '河北', Fujian: '福建', Anhui: '安徽',
  Liaoning: '辽宁', Jiangxi: '江西', Shanxi: '山西', Shaanxi: '陕西',
  Yunnan: '云南', Guizhou: '贵州', Guangxi: '广西', Hainan: '海南',
  Jilin: '吉林', Heilongjiang: '黑龙江', Inner: '内蒙古', Mongolia: '内蒙古',
  'Inner Mongolia': '内蒙古', Xinjiang: '新疆', Tibet: '西藏', Qinghai: '青海',
  Ningxia: '宁夏', Taiwan: '台湾', Hong: '香港', Kong: '香港', Macau: '澳门',
  'Hong Kong': '香港'
}

function toCN(name: string | undefined): string {
  if (!name) return ''
  return CITY_CN[name] || CITY_CN[name.replace(/\s+/g, '')] || name
}

/** 从各 IP 定位服务的 JSON 中解析可读位置 */
function parseIPLocationJson(json: Record<string, unknown>): string | null {
  // ip9.com.cn：返回中文省市区
  if (json.ret === 200 && json.data && typeof json.data === 'object') {
    const d = json.data as Record<string, unknown>
    const parts = [d.prov, d.city, d.area].filter(v => typeof v === 'string' && v)
    if (parts.length) return parts.join(' ')
  }
  // ip-api.com
  if (json.status === 'success') {
    if (typeof json.district === 'string' && json.district) return json.district
    const region = toCN(String(json.regionName || ''))
    const city = toCN(String(json.city || ''))
    if (region || city) return [region, city].filter(Boolean).join(' ')
  }
  // ipinfo.io
  if (json.city && json.region) {
    return `${toCN(String(json.region))} ${toCN(String(json.city))}`.trim()
  }
  return null
}

// 通过 IP 获取地理位置辅助函数（主进程可访问 http/https 模块）
async function tryIPService(url: string): Promise<string | null> {
  return new Promise((resolve) => {
    const mod = url.startsWith('https') ? https : http
    const req = mod.get(url, { timeout: 5000 }, (res) => {
      let data = ''
      res.setEncoding('utf8')
      res.on('data', (chunk) => { data += chunk })
      res.on('end', () => {
        try {
          const json = JSON.parse(data) as Record<string, unknown>
          resolve(parseIPLocationJson(json))
        } catch (e) {
          console.error('[Main] 解析 IP 定位响应失败:', e)
          resolve(null)
        }
      })
    })
    req.on('error', (e) => { console.error('[Main] IP 定位请求错误:', e.message); resolve(null) })
    req.on('timeout', () => { console.error('[Main] IP 定位请求超时'); req.destroy(); resolve(null) })
  })
}

function winFromSender(event: IpcMainEvent | IpcMainInvokeEvent): BrowserWindow | null {
  return BrowserWindow.fromWebContents(event.sender) ?? mainWindow
}

function onMinimize(event: IpcMainEvent) {
  winFromSender(event)?.minimize()
}

function onMaximize(event: IpcMainEvent) {
  const win = winFromSender(event)
  if (!win) return
  if (win.isMaximized()) win.unmaximize()
  else win.maximize()
}

function onClose(event: IpcMainEvent) {
  winFromSender(event)?.close()
}

const MAX_CHANGED = 'window-maximized-changed'

function pushMaximizedState(win: BrowserWindow) {
  win.webContents.send(MAX_CHANGED, win.isMaximized())
}

function registerWindowIpc() {
  ipcMain.removeHandler('window:minimize')
  ipcMain.removeHandler('window:maximize')
  ipcMain.removeHandler('window:close')
  ipcMain.removeHandler('window:is-maximized')
  ipcMain.removeAllListeners('window-minimize')
  ipcMain.removeAllListeners('window-maximize')
  ipcMain.removeAllListeners('window-close')

  ipcMain.removeHandler('window:is-pinned')
  ipcMain.removeHandler('window:toggle-pin')
  ipcMain.removeHandler('app:set-auto-start')
  ipcMain.removeHandler('app:get-auto-start')
  ipcMain.removeHandler('window:set-mode')
  ipcMain.removeHandler('fetch-ip-location')
  ipcMain.removeHandler('secure-storage:is-available')
  ipcMain.removeHandler('secure-storage:get')
  ipcMain.removeHandler('secure-storage:set')
  ipcMain.removeHandler('secure-storage:remove')

  ipcMain.on('window-minimize', onMinimize)
  ipcMain.on('window-maximize', onMaximize)
  ipcMain.on('window-close', onClose)

  ipcMain.handle('window:minimize', e => onMinimize(e))
  ipcMain.handle('window:maximize', e => onMaximize(e))
  ipcMain.handle('window:close', e => onClose(e))
  ipcMain.handle('window:is-maximized', event => {
    const win = winFromSender(event)
    return win ? win.isMaximized() : false
  })
  ipcMain.handle('window:is-pinned', event => {
    const win = winFromSender(event)
    return win ? win.isAlwaysOnTop() : false
  })
  ipcMain.handle('window:toggle-pin', event => {
    const win = winFromSender(event)
    if (win) {
      const isPinned = !win.isAlwaysOnTop()
      win.setAlwaysOnTop(isPinned)
      return isPinned
    }
    return false
  })

  ipcMain.handle('app:set-auto-start', (_event, enabled: boolean) => {
    app.setLoginItemSettings({
      openAtLogin: !!enabled,
      openAsHidden: false
    })
    return true
  })

  ipcMain.handle('app:get-auto-start', () => {
    return app.getLoginItemSettings().openAtLogin
  })

  ipcMain.handle('window:set-mode', (event, mode: 'login' | 'main') => {
    const win = winFromSender(event)
    if (!win) return
    if (mode === 'login') {
      if (win.isMaximized()) win.unmaximize()
      win.setResizable(false)
      win.setMinimumSize(319, 461)
      win.setMaximumSize(319, 461)
      win.setSize(319, 461, false)
      win.center()
      return
    }
    win.setResizable(true)
    win.setMaximumSize(99999, 99999)
    win.setMinimumSize(966, 676)
    if (!win.isMaximized()) {
      win.setSize(1083, 833, false)
      win.center()
    }
  })

  // 通过 IP 获取地理位置（优先国内可访问接口，失败再回退）
  ipcMain.handle('fetch-ip-location', async () => {
    const services = [
      'https://ip9.com.cn/get',
      'https://ipinfo.io/json',
      'http://ip-api.com/json/?fields=status,country,regionName,city,district&lang=zh'
    ]
    for (const url of services) {
      const result = await tryIPService(url)
      if (result) return result
    }
    return null
  })

  ipcMain.handle('secure-storage:is-available', () => safeStorage.isEncryptionAvailable())

  ipcMain.handle('secure-storage:get', (_event, key: string) => {
    if (!isValidKey(key)) return null
    if (!safeStorage.isEncryptionAvailable()) return null
    const file = secureFilePath(key)
    if (!fs.existsSync(file)) return null
    try {
      const encrypted = fs.readFileSync(file)
      return safeStorage.decryptString(encrypted)
    } catch {
      return null
    }
  })

  ipcMain.handle('secure-storage:set', (_event, key: string, value: string) => {
    if (!isValidKey(key)) return false
    if (!safeStorage.isEncryptionAvailable()) return false
    const encrypted = safeStorage.encryptString(value)
    fs.writeFileSync(secureFilePath(key), encrypted)
    return true
  })

  ipcMain.handle('secure-storage:remove', (_event, key: string) => {
    if (!isValidKey(key)) return false
    const file = secureFilePath(key)
    if (fs.existsSync(file)) {
      fs.unlinkSync(file)
    }
    return true
  })
}

registerWindowIpc()

function windowBackgroundColor(theme?: string) {
  return theme === 'dark' ? '#1a1a1a' : '#f5f5f5'
}

function applyAllWindowBackgrounds(theme: string) {
  const color = windowBackgroundColor(theme)
  BrowserWindow.getAllWindows().forEach(win => win.setBackgroundColor(color))
}

ipcMain.on('theme-changed', (_e, theme: string) => {
  applyAllWindowBackgrounds(theme)
})

function createTrayIcon(): Electron.NativeImage {
  const size = 16
  const canvas = Buffer.alloc(size * size * 4)
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const i = (y * size + x) * 4
      const inCircle = (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 49
      if (inCircle) {
        canvas[i] = 18
        canvas[i + 1] = 183
        canvas[i + 2] = 245
        canvas[i + 3] = 255
      }
    }
  }
  return nativeImage.createFromBuffer(canvas, { width: size, height: size })
}

function showMainWindow() {
  if (!mainWindow) {
    createWindow()
    return
  }
  if (mainWindow.isMinimized()) mainWindow.restore()
  mainWindow.show()
  mainWindow.focus()
}

function createTray() {
  if (tray) return
  tray = new Tray(createTrayIcon())
  tray.setToolTip('LinkX')
  const contextMenu = Menu.buildFromTemplate([
    { label: '显示主窗口', click: () => showMainWindow() },
    { type: 'separator' },
    { label: '退出', click: () => app.quit() }
  ])
  tray.setContextMenu(contextMenu)
  tray.on('double-click', () => showMainWindow())
}

function registerGlobalShortcuts() {
  globalShortcut.unregisterAll()
  globalShortcut.register('CommandOrControl+Shift+L', () => {
    if (mainWindow?.isVisible()) {
      mainWindow.hide()
    } else {
      showMainWindow()
    }
  })
}

let momentsWindow: BrowserWindow | null = null

function createMomentsWindow() {
  if (momentsWindow) {
    if (momentsWindow.isMinimized()) momentsWindow.restore()
    momentsWindow.focus()
    return
  }

  momentsWindow = new BrowserWindow({
    width: 440,
    height: 560,
    resizable: false,
    frame: false,
    titleBarStyle: 'hidden',
    transparent: false,
    backgroundMaterial: 'acrylic',
    backgroundColor: '#f5f5f5',
    show: false,
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  })

  momentsWindow.once('ready-to-show', () => {
    momentsWindow?.show()
  })

  if (isDev && process.env.VITE_DEV_SERVER_URL) {
    momentsWindow.loadURL(process.env.VITE_DEV_SERVER_URL + '#/moments')
  } else {
    momentsWindow.loadFile(path.join(__dirname, '../../dist/index.html'), { hash: 'moments' })
  }

  momentsWindow.on('closed', () => {
    momentsWindow = null
  })
}

ipcMain.on('window-open-moments', () => {
  createMomentsWindow()
})

/** 发布窗口通知：友链列表窗口刷新 */
ipcMain.on('moments:published', () => {
  if (momentsWindow && !momentsWindow.isDestroyed()) {
    momentsWindow.webContents.send('moments:refresh')
    if (momentsWindow.isMinimized()) momentsWindow.restore()
  }
})

// 友链-发布文字独立窗口
let momentsTextWindow: BrowserWindow | null = null

function createMomentsTextWindow() {
  if (momentsTextWindow) {
    if (momentsTextWindow.isMinimized()) momentsTextWindow.restore()
    momentsTextWindow.focus()
    return
  }

  momentsTextWindow = new BrowserWindow({
    width: 420,
    height: 520,
    resizable: false,
    frame: false,
    titleBarStyle: 'hidden',
    transparent: false,
    backgroundMaterial: 'acrylic',
    backgroundColor: '#f5f5f5',
    show: false,
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  })

  momentsTextWindow.once('ready-to-show', () => {
    momentsTextWindow?.show()
  })

  if (isDev && process.env.VITE_DEV_SERVER_URL) {
    momentsTextWindow.loadURL(process.env.VITE_DEV_SERVER_URL + '#/moments/text')
  } else {
    momentsTextWindow.loadFile(path.join(__dirname, '../../dist/index.html'), { hash: 'moments/text' })
  }

  momentsTextWindow.on('closed', () => {
    momentsTextWindow = null
  })
}

ipcMain.on('window-open-moments-text', () => {
  createMomentsTextWindow()
})

// 友链-发布图片/视频独立窗口
let momentsMediaWindow: BrowserWindow | null = null

function createMomentsMediaWindow() {
  if (momentsMediaWindow) {
    if (momentsMediaWindow.isMinimized()) momentsMediaWindow.restore()
    momentsMediaWindow.focus()
    return
  }

  momentsMediaWindow = new BrowserWindow({
    width: 480,
    height: 600,
    resizable: false,
    frame: false,
    titleBarStyle: 'hidden',
    transparent: false,
    backgroundMaterial: 'acrylic',
    backgroundColor: '#f5f5f5',
    show: false,
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  })

  momentsMediaWindow.once('ready-to-show', () => {
    momentsMediaWindow?.show()
  })

  if (isDev && process.env.VITE_DEV_SERVER_URL) {
    momentsMediaWindow.loadURL(process.env.VITE_DEV_SERVER_URL + '#/moments/media')
  } else {
    momentsMediaWindow.loadFile(path.join(__dirname, '../../dist/index.html'), { hash: 'moments/media' })
  }

  momentsMediaWindow.on('closed', () => {
    momentsMediaWindow = null
  })
}

ipcMain.on('window-open-moments-media', () => {
  createMomentsMediaWindow()
})

let noteEditorWindow: BrowserWindow | null = null

function createNoteEditorWindow() {
  if (noteEditorWindow) {
    if (noteEditorWindow.isMinimized()) noteEditorWindow.restore()
    noteEditorWindow.focus()
    return
  }

  noteEditorWindow = new BrowserWindow({
    width: 800,
    height: 600,
    minWidth: 600,
    minHeight: 400,
    frame: false,
    titleBarStyle: 'hidden',
    transparent: false,
    backgroundMaterial: 'mica',
    backgroundColor: '#f5f5f5',
    show: false,
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  })

  noteEditorWindow.once('ready-to-show', () => {
    noteEditorWindow?.show()
  })

  if (isDev && process.env.VITE_DEV_SERVER_URL) {
    noteEditorWindow.loadURL(process.env.VITE_DEV_SERVER_URL + '#/note-editor')
  } else {
    noteEditorWindow.loadFile(path.join(__dirname, '../../dist/index.html'), { hash: 'note-editor' })
  }

  noteEditorWindow.on('maximize', () => {
    if (noteEditorWindow) pushMaximizedState(noteEditorWindow)
  })
  noteEditorWindow.on('unmaximize', () => {
    if (noteEditorWindow) pushMaximizedState(noteEditorWindow)
  })

  noteEditorWindow.on('closed', () => {
    noteEditorWindow = null
  })
}

ipcMain.on('window-open-note-editor', () => {
  createNoteEditorWindow()
})

let registerWindow: BrowserWindow | null = null

function createRegisterWindow() {
  if (registerWindow) {
    if (registerWindow.isMinimized()) registerWindow.restore()
    registerWindow.focus()
    return
  }

  registerWindow = new BrowserWindow({
    width: 360,
    height: 560,
    resizable: false,
    frame: false,
    titleBarStyle: 'hidden',
    transparent: false,
    backgroundMaterial: 'mica',
    backgroundColor: '#eef5fb',
    show: false,
    // 不挂 parent，避免盖住登录窗；作为独立弹窗并列显示
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  })

  // 放在登录窗右侧，登录页保持可见
  if (mainWindow && !mainWindow.isDestroyed()) {
    const [mx, my] = mainWindow.getPosition()
    const [mw] = mainWindow.getSize()
    registerWindow.setPosition(mx + mw + 12, my)
  } else {
    registerWindow.center()
  }

  registerWindow.once('ready-to-show', () => {
    registerWindow?.show()
  })

  if (isDev && process.env.VITE_DEV_SERVER_URL) {
    registerWindow.loadURL(process.env.VITE_DEV_SERVER_URL + '#/register')
  } else {
    registerWindow.loadFile(path.join(__dirname, '../../dist/index.html'), { hash: 'register' })
  }

  registerWindow.on('closed', () => {
    registerWindow = null
  })
}

ipcMain.on('window-open-register', () => {
  createRegisterWindow()
})

function createWindow() {
  if (isDev) {
    console.log('[electron] preload:', preloadPath, 'exists:', fs.existsSync(preloadPath))
  }

  mainWindow = new BrowserWindow({
    width: 319,
    height: 461,
    minWidth: 319,
    minHeight: 461,
    maxWidth: 319,
    maxHeight: 461,
    resizable: false,
    frame: false,
    titleBarStyle: 'hidden',
    transparent: false,
    backgroundMaterial: 'mica',
    backgroundColor: '#f5f5f5',
    show: false,
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webviewTag: true
    }
  })

  mainWindow.once('ready-to-show', () => {
    mainWindow?.show()
  })

  if (isDev && process.env.VITE_DEV_SERVER_URL) {
    mainWindow.loadURL(process.env.VITE_DEV_SERVER_URL)
  } else {
    mainWindow.loadFile(path.join(__dirname, '../../dist/index.html'))
  }

  mainWindow.webContents.on('preload-error', (_e, preloadFile, err) => {
    console.error('[electron] preload-error:', preloadFile, err)
  })

  mainWindow.on('maximize', () => {
    if (mainWindow) pushMaximizedState(mainWindow)
  })
  mainWindow.on('unmaximize', () => {
    if (mainWindow) pushMaximizedState(mainWindow)
  })

  mainWindow.webContents.on('did-finish-load', () => {
    if (mainWindow) pushMaximizedState(mainWindow)
    mainWindow?.webContents
      .executeJavaScript('typeof window.electronAPI !== "undefined"')
      .then(ok => {
        if (!ok) {
          console.error('[electron] window.electronAPI 未注入，preload:', preloadPath)
        } else {
          console.log('[electron] electronAPI OK')
        }
      })
      .catch(() => {})
  })

  mainWindow.on('close', (e) => {
    if (process.platform === 'darwin') return
    if (!tray) return
    e.preventDefault()
    mainWindow?.hide()
  })

  mainWindow.on('closed', () => {
    mainWindow = null
  })
}

app.whenReady().then(() => {
  // 设置严格的 Content Security Policy，防止 Electron Security Warning
  // 在窗口创建前设置，应用到所有窗口
  const csp = [
    "default-src 'self';",
    "script-src 'self' 'unsafe-inline';",
    "style-src 'self' 'unsafe-inline';",
    "img-src 'self' data: blob: https: http:;",
    "font-src 'self' data:;",
    "connect-src 'self' ws: wss: http: https:;",
    "media-src 'self' blob:;"
  ].join(' ')

  app.on('web-contents-created', (_event, contents) => {
    contents.session.webRequest.onHeadersReceived((details, callback) => {
      callback({
        responseHeaders: {
          ...details.responseHeaders,
          'Content-Security-Policy': [csp]
        }
      })
    })
  })

  registerWindowIpc()
  createWindow()
  createTray()
  registerGlobalShortcuts()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow()
    } else {
      showMainWindow()
    }
  })
})

app.on('will-quit', () => {
  globalShortcut.unregisterAll()
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})
