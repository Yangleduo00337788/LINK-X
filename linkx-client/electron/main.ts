import { app, BrowserWindow, ipcMain, Tray, Menu, nativeImage, globalShortcut, safeStorage, desktopCapturer, Notification, net, type IpcMainEvent, type IpcMainInvokeEvent, type WebRequestHeadersReceivedCallbackParams, type OnHeadersReceivedListener } from 'electron'
import path from 'node:path'
import fs from 'node:fs'
import http from 'node:http'
import https from 'node:https'
import { fileURLToPath } from 'node:url'
import { Buffer } from 'node:buffer'

/** 渲染进程发起的受控下载请求 */
type DownloadFilePayload = {
  url?: string
  /** 原始二进制（blob / data URL 等由渲染进程读入后传入） */
  data?: ArrayBuffer | Uint8Array
  fileName?: string
  /** 自定义下载目录；空则用系统 Downloads */
  directory?: string
  /** true：每次弹出另存为；false：直接写入下载目录 */
  askEveryTime?: boolean
}

function sanitizeFileName(name: string): string {
  const base = (name || 'download').replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_').trim()
  return base.slice(0, 180) || 'download'
}

function resolveDownloadDir(custom?: string): string {
  const trimmed = (custom || '').trim()
  if (trimmed) {
    try {
      if (!fs.existsSync(trimmed)) {
        fs.mkdirSync(trimmed, { recursive: true })
      }
      if (fs.statSync(trimmed).isDirectory()) {
        return trimmed
      }
    } catch {
      /* fall through to system downloads */
    }
  }
  return app.getPath('downloads')
}

/** 若目标已存在则追加 (1)、(2)… */
function uniqueSavePath(dir: string, fileName: string): string {
  const safe = sanitizeFileName(fileName)
  const ext = path.extname(safe)
  const stem = path.basename(safe, ext)
  let candidate = path.join(dir, safe)
  let i = 1
  while (fs.existsSync(candidate)) {
    candidate = path.join(dir, `${stem} (${i})${ext}`)
    i += 1
  }
  return candidate
}

async function readDownloadBytes(payload: DownloadFilePayload): Promise<Buffer> {
  if (payload.data != null) {
    return Buffer.from(payload.data instanceof ArrayBuffer ? new Uint8Array(payload.data) : payload.data)
  }
  const url = (payload.url || '').trim()
  if (!url) {
    throw new Error('缺少下载内容')
  }
  if (/^https?:\/\//i.test(url)) {
    const res = await net.fetch(url)
    if (!res.ok) {
      throw new Error(`下载失败 (${res.status})`)
    }
    return Buffer.from(await res.arrayBuffer())
  }
  throw new Error('不支持的下载地址，请由渲染进程传入文件数据')
}

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const isDev = Boolean(process.env.VITE_DEV_SERVER_URL)

/** Windows/Linux 使用系统原生标题栏按钮（Win11 Caption Buttons） */
const USE_NATIVE_TITLE_BAR_OVERLAY = process.platform === 'win32' || process.platform === 'linux'
let currentUiTheme: 'light' | 'dark' = 'light'

/** 全局快捷键（可由设置页覆盖） */
const currentShortcuts = {
  toggleWindow: 'CommandOrControl+Shift+L',
  lock: 'CommandOrControl+Shift+K'
}

type TitleBarOverlayOpts = {
  color: string
  symbolColor: string
  height: number
}

function buildTitleBarOverlay(
  theme: string = currentUiTheme,
  height = 40
): TitleBarOverlayOpts | undefined {
  if (!USE_NATIVE_TITLE_BAR_OVERLAY) return undefined
  const dark = theme === 'dark'
  return {
    // 透明底贴合 mica/acrylic，图标随明暗主题
    color: dark ? '#1a1a1a00' : '#f5f5f500',
    symbolColor: dark ? '#e6e6e6' : '#1f1f1f',
    height
  }
}

function applyTitleBarOverlay(win: BrowserWindow, theme?: string, height = 40) {
  const opts = buildTitleBarOverlay(theme ?? currentUiTheme, height)
  if (!opts || win.isDestroyed()) return
  try {
    win.setTitleBarOverlay(opts)
  } catch (e) {
    console.warn('[electron] setTitleBarOverlay failed:', e)
  }
}

/** 无边框 + 可选原生窗控 */
function framelessChrome(overlayHeight = 40): {
  frame: false
  titleBarStyle: 'hidden'
  titleBarOverlay?: TitleBarOverlayOpts
} {
  const overlay = buildTitleBarOverlay(currentUiTheme, overlayHeight)
  return {
    frame: false,
    titleBarStyle: 'hidden',
    ...(overlay ? { titleBarOverlay: overlay } : {})
  }
}

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

// Windows 通知：开发态（未打包）必须用 execPath 作为 AUMID，否则 Toast 会 HRESULT 失败
if (process.platform === 'win32') {
  const unpackaged = !app.isPackaged || process.defaultApp || /electron\.exe$/i.test(process.execPath)
  app.setAppUserModelId(unpackaged ? process.execPath : 'com.linkx.app')
}

if (isDev) {
  app.commandLine.appendSwitch('--disable-software-rasterizer')
}

// 启用地理位置支持
app.commandLine.appendSwitch('--enable-geolocation')
app.commandLine.appendSwitch('--use-fake-ui-for-media-stream')
app.commandLine.appendSwitch('--use-fake-device-for-media-stream')

let mainWindow: BrowserWindow | null = null
let tray: Tray | null = null
/** 主动退出时跳过「关窗最小化到托盘」 */
let isQuitting = false

type OpenOnStartup = 'main' | 'tray'
type AppLanguage = 'zh-CN' | 'en-US'

interface DesktopPrefs {
  minimizeToTray: boolean
  openOnStartup: OpenOnStartup
  language: AppLanguage
}

const DEFAULT_DESKTOP_PREFS: DesktopPrefs = {
  minimizeToTray: true,
  openOnStartup: 'main',
  language: 'zh-CN'
}

let desktopPrefs: DesktopPrefs = { ...DEFAULT_DESKTOP_PREFS }

function desktopPrefsPath() {
  return path.join(app.getPath('userData'), 'desktop-prefs.json')
}

function loadDesktopPrefs(): DesktopPrefs {
  try {
    const raw = fs.readFileSync(desktopPrefsPath(), 'utf8')
    const parsed = JSON.parse(raw) as Partial<DesktopPrefs>
    return {
      minimizeToTray:
        typeof parsed.minimizeToTray === 'boolean'
          ? parsed.minimizeToTray
          : DEFAULT_DESKTOP_PREFS.minimizeToTray,
      openOnStartup: parsed.openOnStartup === 'tray' ? 'tray' : 'main',
      language: parsed.language === 'en-US' ? 'en-US' : 'zh-CN'
    }
  } catch {
    return { ...DEFAULT_DESKTOP_PREFS }
  }
}

function saveDesktopPrefs(next: DesktopPrefs) {
  desktopPrefs = { ...next }
  try {
    fs.writeFileSync(desktopPrefsPath(), JSON.stringify(desktopPrefs, null, 2), 'utf8')
  } catch (e) {
    console.warn('[electron] save desktop prefs failed:', e)
  }
}

function syncLoginItemHidden() {
  try {
    const { openAtLogin } = app.getLoginItemSettings()
    if (!openAtLogin) return
    app.setLoginItemSettings({
      openAtLogin: true,
      openAsHidden: desktopPrefs.openOnStartup === 'tray'
    })
  } catch (e) {
    console.warn('[electron] sync login item hidden failed:', e)
  }
}

function trayMenuLabels() {
  if (desktopPrefs.language === 'en-US') {
    return { show: 'Show LinkX', quit: 'Quit' }
  }
  return { show: '显示主窗口', quit: '退出' }
}

function rebuildTrayMenu() {
  if (!tray || tray.isDestroyed()) return
  const labels = trayMenuLabels()
  tray.setContextMenu(
    Menu.buildFromTemplate([
      { label: labels.show, click: () => showMainWindow() },
      { type: 'separator' },
      {
        label: labels.quit,
        click: () => {
          isQuitting = true
          app.quit()
        }
      }
    ])
  )
}

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
  ipcMain.removeHandler('app:get-desktop-prefs')
  ipcMain.removeHandler('app:set-desktop-prefs')
  ipcMain.removeHandler('window:set-mode')
  ipcMain.removeHandler('fetch-ip-location')
  ipcMain.removeHandler('secure-storage:is-available')
  ipcMain.removeHandler('secure-storage:get')
  ipcMain.removeHandler('secure-storage:set')
  ipcMain.removeHandler('secure-storage:remove')
  ipcMain.removeHandler('app:show-notification')
  ipcMain.removeHandler('app:pick-download-path')
  ipcMain.removeHandler('app:open-download-path')
  ipcMain.removeHandler('app:clear-cache')
  ipcMain.removeHandler('app:download-file')
  ipcMain.removeHandler('app:download-and-install-update')
  ipcMain.removeHandler('app:set-shortcuts')
  ipcMain.removeHandler('app:get-shortcuts')
  ipcMain.removeHandler('app:get-download-path')

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
      openAsHidden: !!enabled && desktopPrefs.openOnStartup === 'tray'
    })
    return true
  })

  ipcMain.handle('app:get-auto-start', () => {
    return app.getLoginItemSettings().openAtLogin
  })

  ipcMain.handle('app:get-desktop-prefs', () => ({ ...desktopPrefs }))

  ipcMain.handle(
    'app:set-desktop-prefs',
    (
      _event,
      patch: Partial<DesktopPrefs> | null | undefined
    ): DesktopPrefs => {
      const next: DesktopPrefs = {
        minimizeToTray:
          typeof patch?.minimizeToTray === 'boolean'
            ? patch.minimizeToTray
            : desktopPrefs.minimizeToTray,
        openOnStartup: patch?.openOnStartup === 'tray' ? 'tray' : patch?.openOnStartup === 'main' ? 'main' : desktopPrefs.openOnStartup,
        language:
          patch?.language === 'en-US'
            ? 'en-US'
            : patch?.language === 'zh-CN'
              ? 'zh-CN'
              : desktopPrefs.language
      }
      saveDesktopPrefs(next)
      rebuildTrayMenu()
      syncLoginItemHidden()
      return { ...desktopPrefs }
    }
  )

  ipcMain.handle('window:set-mode', (event, mode: 'login' | 'main') => {
    const win = winFromSender(event)
    if (!win) return
    if (mode === 'login') {
      if (win.isMaximized()) win.unmaximize()
      win.setMaximizable(false)
      win.setResizable(false)
      win.setMinimumSize(319, 461)
      win.setMaximumSize(319, 461)
      win.setSize(319, 461, false)
      win.center()
      return
    }
    // 登录窗创建时因 maxSize 锁定，系统会关掉可最大化；切主界面需显式恢复，否则 titleBarOverlay 不显示最大化按钮
    win.setMaximumSize(99999, 99999)
    win.setMinimumSize(966, 676)
    win.setResizable(true)
    win.setMaximizable(true)
    if (!win.isMaximized()) {
      win.setSize(1083, 833, false)
      win.center()
    }
  })

  ipcMain.handle('app:get-download-path', () => {
    return app.getPath('downloads')
  })

  ipcMain.handle('app:pick-download-path', async event => {
    const win = winFromSender(event)
    const { dialog } = await import('electron')
    const result = await dialog.showOpenDialog(win ?? undefined, {
      properties: ['openDirectory', 'createDirectory']
    })
    if (result.canceled || !result.filePaths[0]) return null
    return result.filePaths[0]
  })

  /** 原生多选图片；返回文件名 + MIME + 二进制，供渲染进程构造成 File 再上传 */
  ipcMain.handle('app:pick-images', async event => {
    const win = winFromSender(event)
    const { dialog } = await import('electron')
    const result = await dialog.showOpenDialog(win ?? undefined, {
      title: '选择图片',
      properties: ['openFile', 'multiSelections'],
      filters: [{ name: 'Images', extensions: ['jpg', 'jpeg', 'png', 'gif', 'webp'] }]
    })
    if (result.canceled || !result.filePaths.length) return [] as Array<{
      name: string
      mimeType: string
      data: Buffer
    }>

    const mimeByExt: Record<string, string> = {
      '.jpg': 'image/jpeg',
      '.jpeg': 'image/jpeg',
      '.png': 'image/png',
      '.gif': 'image/gif',
      '.webp': 'image/webp'
    }
    const maxBytes = 10 * 1024 * 1024
    const files: Array<{ name: string; mimeType: string; data: Buffer }> = []
    for (const filePath of result.filePaths) {
      const stat = await fs.promises.stat(filePath)
      if (stat.size <= 0 || stat.size > maxBytes) continue
      const ext = path.extname(filePath).toLowerCase()
      const mimeType = mimeByExt[ext]
      if (!mimeType) continue
      const data = await fs.promises.readFile(filePath)
      files.push({ name: path.basename(filePath), mimeType, data })
    }
    return files
  })

  ipcMain.handle('app:open-download-path', async (_event, customPath?: string) => {
    const { shell } = await import('electron')
    const target = customPath && customPath.trim() ? customPath : app.getPath('downloads')
    const err = await shell.openPath(target)
    return !err
  })

  ipcMain.handle('app:clear-cache', async () => {
    try {
      const { session } = await import('electron')
      await session.defaultSession.clearCache()
      await session.defaultSession.clearStorageData({
        storages: ['cachestorage', 'shadercache', 'serviceworkers']
      })
      return { ok: true, message: '缓存已清理' }
    } catch (e) {
      return { ok: false, message: e instanceof Error ? e.message : '清理失败' }
    }
  })

  /** 按设置写入本地：询问保存位置 / 自动保存到下载目录 */
  ipcMain.handle('app:download-file', async (event, payload: DownloadFilePayload = {}) => {
    try {
      const fileName = sanitizeFileName(payload.fileName || 'download')
      const dir = resolveDownloadDir(payload.directory)
      if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true })
      }

      let targetPath: string
      if (payload.askEveryTime !== false) {
        const win = winFromSender(event)
        const { dialog } = await import('electron')
        const result = await dialog.showSaveDialog(win ?? undefined, {
          defaultPath: path.join(dir, fileName),
          filters: [{ name: 'All Files', extensions: ['*'] }]
        })
        if (result.canceled || !result.filePath) {
          return { ok: false, canceled: true }
        }
        targetPath = result.filePath
      } else {
        targetPath = uniqueSavePath(dir, fileName)
      }

      const bytes = await readDownloadBytes(payload)
      await fs.promises.writeFile(targetPath, bytes)
      return { ok: true, path: targetPath }
    } catch (e) {
      return { ok: false, message: e instanceof Error ? e.message : '下载失败' }
    }
  })

  /**
   * 检查更新后的自动下载安装：下载安装包到临时目录并拉起系统安装程序。
   * Windows 上 .exe/.msi 会进入安装向导；完成后由安装程序自行处理覆盖。
   */
  ipcMain.handle(
    'app:download-and-install-update',
    async (event, payload: { url?: string; version?: string; fileName?: string } = {}) => {
      try {
        const url = (payload.url || '').trim()
        if (!/^https?:\/\//i.test(url)) {
          return { ok: false, message: '无效的下载地址' }
        }

        let fileName = sanitizeFileName(payload.fileName || '')
        if (!fileName || fileName === 'download') {
          try {
            const fromUrl = path.basename(new URL(url).pathname)
            fileName = sanitizeFileName(fromUrl || '')
          } catch {
            fileName = ''
          }
        }
        if (!fileName || fileName === 'download') {
          const ver = sanitizeFileName(payload.version || 'update')
          fileName = `LinkX-Setup-${ver}.exe`
        }

        const dir = path.join(app.getPath('temp'), 'LinkX-Update')
        if (!fs.existsSync(dir)) {
          fs.mkdirSync(dir, { recursive: true })
        }
        const targetPath = path.join(dir, fileName)

        const win = winFromSender(event)
        win?.webContents.send('app:update-progress', { phase: 'downloading', percent: 0 })

        const res = await net.fetch(url)
        if (!res.ok) {
          return { ok: false, message: `下载失败 (${res.status})` }
        }
        const bytes = Buffer.from(await res.arrayBuffer())
        await fs.promises.writeFile(targetPath, bytes)

        win?.webContents.send('app:update-progress', { phase: 'installing', percent: 100 })

        const { shell } = await import('electron')
        const openErr = await shell.openPath(targetPath)
        if (openErr) {
          // 无法直接执行时，至少打开所在目录让用户手动安装
          await shell.showItemInFolder(targetPath)
          return {
            ok: true,
            path: targetPath,
            launched: false,
            message: '已下载，请在打开的文件夹中手动运行安装包'
          }
        }

        // 安装程序拉起后退出应用，避免文件占用导致覆盖失败
        setTimeout(() => {
          app.quit()
        }, 800)

        return { ok: true, path: targetPath, launched: true }
      } catch (e) {
        return { ok: false, message: e instanceof Error ? e.message : '下载安装失败' }
      }
    }
  )

  ipcMain.handle('app:get-shortcuts', () => ({ ...currentShortcuts }))

  ipcMain.handle('app:set-shortcuts', (_event, payload: { toggleWindow?: string; lock?: string }) => {
    if (payload?.toggleWindow) currentShortcuts.toggleWindow = String(payload.toggleWindow)
    if (payload?.lock) currentShortcuts.lock = String(payload.lock)
    return registerGlobalShortcuts()
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

  // 系统桌面通知（日程提醒、新消息等）
  ipcMain.handle(
    'app:show-notification',
    async (_event, payload: { title?: string; body?: string; silent?: boolean }) => {
      const title = (payload?.title || 'LinkX').trim() || 'LinkX'
      const body = (payload?.body || '').trim()
      const silent = !!payload?.silent
      return showDesktopNotice(title, body, silent)
    }
  )
}

/** 广播应用内 toast，保证用户一定能看到 */
function broadcastInAppToast(title: string, body: string) {
  for (const win of BrowserWindow.getAllWindows()) {
    if (!win.isDestroyed()) {
      win.webContents.send('app:in-app-toast', { title, body })
    }
  }
}

/**
 * 可靠桌面提醒：
 * - 开发态 Windows：跳过 Electron Toast（常 HRESULT 失败），用托盘气球 + 应用内 toast
 * - 打包后：优先系统 Toast，失败再托盘气球
 * - 始终推送应用内 toast
 */
function isUnpackagedWindows(): boolean {
  return (
    process.platform === 'win32' &&
    (!app.isPackaged || !!process.defaultApp || /electron\.exe$/i.test(process.execPath))
  )
}

async function showDesktopNotice(title: string, body: string, silent = false): Promise<boolean> {
  broadcastInAppToast(title, body)

  // 未打包 Windows 上 Notification 几乎必定失败（缺少 Start Menu 快捷方式），直接跳过避免误报日志
  const tryNativeToast = Notification.isSupported() && !isUnpackagedWindows()

  if (tryNativeToast) {
    const toastOk = await new Promise<boolean>(resolve => {
      try {
        const n = new Notification({
          title,
          body,
          silent,
          icon: createTrayIcon()
        })
        let settled = false
        const done = (ok: boolean) => {
          if (settled) return
          settled = true
          resolve(ok)
        }
        n.on('failed', (_e, err) => {
          console.warn('[Main] Notification failed:', err)
          done(false)
        })
        // 不信任 show：Windows 上可能 show 后又 failed
        n.show()
        setTimeout(() => done(true), 800)
      } catch (e) {
        console.error('[Main] Notification error:', e)
        resolve(false)
      }
    })
    if (toastOk) {
      console.log('[Main] Notification OK')
      return true
    }
  }

  if (tray && !tray.isDestroyed()) {
    try {
      tray.displayBalloon({
        title,
        content: body || ' ',
        iconType: 'info'
      })
      console.log('[Main] Remind via tray balloon + in-app toast')
      return true
    } catch (e) {
      console.error('[Main] Tray balloon failed:', e)
    }
  }

  console.log('[Main] Remind via in-app toast only')
  return true
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
  if (theme === 'light' || theme === 'dark') {
    currentUiTheme = theme
  }
  applyAllWindowBackgrounds(theme)
  for (const win of BrowserWindow.getAllWindows()) {
    applyTitleBarOverlay(win, theme)
  }
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
  rebuildTrayMenu()
  tray.on('double-click', () => showMainWindow())
}

function registerGlobalShortcuts(): boolean {
  globalShortcut.unregisterAll()
  let ok = true
  try {
    const toggleOk = globalShortcut.register(currentShortcuts.toggleWindow, () => {
      if (mainWindow?.isVisible()) {
        mainWindow.hide()
      } else {
        showMainWindow()
      }
    })
    if (!toggleOk) ok = false
  } catch {
    ok = false
  }
  try {
    const lockOk = globalShortcut.register(currentShortcuts.lock, () => {
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send('app:shortcut-lock')
        showMainWindow()
      }
    })
    if (!lockOk) ok = false
  } catch {
    ok = false
  }
  return ok
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
    ...framelessChrome(40),
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
    ...framelessChrome(40),
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
    ...framelessChrome(40),
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
    ...framelessChrome(40),
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
    ...framelessChrome(40),
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
    ...framelessChrome(40),
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
    // 「启动时打开 = 托盘」：仅驻留托盘，不弹出主窗口
    if (desktopPrefs.openOnStartup === 'tray') {
      return
    }
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
    if (isQuitting || process.platform === 'darwin') return
    if (!desktopPrefs.minimizeToTray || !tray) return
    e.preventDefault()
    mainWindow?.hide()
  })

  mainWindow.on('closed', () => {
    mainWindow = null
  })
}

app.whenReady().then(() => {
  desktopPrefs = loadDesktopPrefs()

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
  // 先建托盘，确保「启动到托盘 / 关窗进托盘」可用
  createTray()
  createWindow()
  registerGlobalShortcuts()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow()
    } else {
      showMainWindow()
    }
  })
})

app.on('before-quit', () => {
  isQuitting = true
})

app.on('will-quit', () => {
  globalShortcut.unregisterAll()
})

app.on('window-all-closed', () => {
  // 最小化到托盘时主窗口仍在（仅 hide），不会走到这里；
  // 关闭即退出时正常结束进程。
  if (process.platform !== 'darwin') {
    app.quit()
  }
})
