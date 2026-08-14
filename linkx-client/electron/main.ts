/**
 * 作者：yangleduo
 */
import { app, BrowserWindow, ipcMain, Tray, Menu, nativeImage, globalShortcut, safeStorage, desktopCapturer, dialog, Notification, net, session, clipboard, shell, screen, type IpcMainEvent, type IpcMainInvokeEvent, type WebRequestHeadersReceivedCallbackParams, type OnHeadersReceivedListener } from 'electron'
import path from 'node:path'
import fs from 'node:fs'
import http from 'node:http'
import https from 'node:https'
import { fileURLToPath } from 'node:url'
import { Buffer } from 'node:buffer'
import { execSync, spawn } from 'node:child_process'
import {
  connectOriginsForCsp,
  expandLoopbackOrigins,
  originFromBaseUrl,
  resolveApiBaseUrl,
  resolveWsBaseUrl
} from '../shared/endpoints'
import { mainT } from './mainI18n'
import { buildHelpPageUrl, resolveHelpPageBaseUrl } from '../shared/helpPage'

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
  /** true：保存后用系统默认程序打开 */
  openAfter?: boolean
}

function sanitizeFileName(name: string): string {
  const base = (name || 'download').replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_').trim()
  return base.slice(0, 180) || 'download'
}

/** openAfter 禁止自动打开的可执行/脚本扩展名（防钓鱼文件静默执行） */
const DANGEROUS_OPEN_EXTENSIONS = new Set([
  '.exe',
  '.msi',
  '.bat',
  '.cmd',
  '.com',
  '.ps1',
  '.vbs',
  '.js',
  '.jar',
  '.scr',
  '.pif',
  '.reg',
  '.dll'
])

function isDangerousOpenExtension(filePath: string): boolean {
  const ext = path.extname(filePath).toLowerCase()
  return DANGEROUS_OPEN_EXTENSIONS.has(ext)
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

/** 允许主进程直接拉取的下载源：API + MinIO/CDN 等可信媒体域 */
function isAllowedDownloadOrigin(urlOrigin: string): boolean {
  const allowed = new Set<string>()
  for (const origin of expandLoopbackOrigins(originFromBaseUrl(resolveApiBaseUrl(process.env.VITE_API_BASE_URL)))) {
    allowed.add(origin)
  }
  for (const part of collectTrustedMediaOrigins().split(/\s+/)) {
    if (part) allowed.add(part)
  }
  return allowed.has(urlOrigin)
}

async function readDownloadBytes(payload: DownloadFilePayload): Promise<Buffer> {
  if (payload.data != null) {
    return Buffer.from(payload.data instanceof ArrayBuffer ? new Uint8Array(payload.data) : payload.data)
  }
  const url = (payload.url || '').trim()
  if (!url) {
    throw new Error(mainT(desktopPrefs.language, 'downloadMissingContent'))
  }
  if (/^https?:\/\//i.test(url)) {
    // 限制下载源为可信 API / MinIO 域，防 SSRF / 任意 URL 下载到磁盘
    let urlOrigin: string
    try {
      urlOrigin = new URL(url).origin
    } catch {
      throw new Error(mainT(desktopPrefs.language, 'downloadBadUrl'))
    }
    if (!isAllowedDownloadOrigin(urlOrigin)) {
      throw new Error(mainT(desktopPrefs.language, 'downloadUntrustedSource'))
    }
    const res = await net.fetch(url)
    if (!res.ok) {
      throw new Error(mainT(desktopPrefs.language, 'downloadFailStatus', { status: res.status }))
    }
    return Buffer.from(await res.arrayBuffer())
  }
  throw new Error(mainT(desktopPrefs.language, 'downloadUnsupportedUrl'))
}

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const isDev = Boolean(process.env.VITE_DEV_SERVER_URL)

/**
 * CSP 可信媒体源：本机 API/MinIO + 可选环境变量（生产 MinIO/CDN 网关）。
 * 朋友圈外链改走后端 /media/external 代理，故不再需要任意 https:。
 */
function collectLoopbackMinioOrigins(apiBase: string): string[] {
  try {
    const u = new URL(apiBase)
    if (u.hostname !== 'localhost' && u.hostname !== '127.0.0.1' && u.hostname !== '[::1]') {
      return []
    }
    const host = u.hostname === 'localhost' ? '127.0.0.1' : u.hostname
    return expandLoopbackOrigins(`${u.protocol}//${host}:9000`)
  } catch {
    return []
  }
}

function collectTrustedMediaOrigins(): string {
  const origins = new Set<string>()
  if (isDev) {
    origins.add('http://127.0.0.1:9000')
    origins.add('http://localhost:9000')
    origins.add('http://127.0.0.1:8080')
    origins.add('http://localhost:8080')
    origins.add('http://127.0.0.1:5173')
    origins.add('http://localhost:5173')
  }
  const apiBase = resolveApiBaseUrl(process.env.VITE_API_BASE_URL)
  for (const raw of [
    process.env.VITE_API_BASE_URL,
    process.env.VITE_MINIO_PUBLIC_ORIGIN,
    process.env.LINKX_MINIO_PUBLIC_ORIGIN
  ]) {
    if (!raw) continue
    try {
      const u = new URL(raw)
      for (const origin of expandLoopbackOrigins(`${u.protocol}//${u.host}`)) {
        origins.add(origin)
      }
    } catch {
      // ignore invalid
    }
  }
  for (const origin of expandLoopbackOrigins(originFromBaseUrl(apiBase))) {
    origins.add(origin)
  }
  // 头像/附件预签名 URL 直连 MinIO（:9000），须写入 CSP img-src，否则 Electron 内裂图
  for (const origin of collectLoopbackMinioOrigins(apiBase)) {
    origins.add(origin)
  }
  return [...origins].join(' ')
}

let currentUiTheme: 'light' | 'dark' = 'light'

/** 全局快捷键（可由设置页覆盖） */
const currentShortcuts = {
  toggleWindow: 'CommandOrControl+Shift+L',
  lock: 'CommandOrControl+Shift+K'
}

/**
 * 透明窗清空色：须与主题匹配，否则圆角抗锯齿会预乘出黑/白脏边。
 * 必须用 rgba()，勿用 #RRGGBBAA / #AARRGGBB——八位 hex 在 Electron/Chromium
 * 两端格式易歧义（例如 #FFFFFF00 会被当成不透明黄）。
 */
function windowBackgroundColor(theme: string = currentUiTheme) {
  return theme === 'dark' ? 'rgba(26, 26, 26, 1)' : 'rgba(255, 255, 255, 0)'
}

/** 无边框；大圆角由渲染层 CSS 绘制；窗控由前端自绘以便裁进圆角 */
function framelessChrome(): {
  frame: false
  titleBarStyle: 'hidden' | 'hiddenInset'
  transparent: true
  backgroundColor: string
  roundedCorners: false
  /** 系统阴影跟矩形 HWND，会在 CSS 圆角外露脏角；轮廓改由 CSS inset 描边 */
  hasShadow: false
} {
  return {
    frame: false,
    titleBarStyle: process.platform === 'darwin' ? 'hiddenInset' : 'hidden',
    transparent: true,
    backgroundColor: windowBackgroundColor(),
    roundedCorners: false,
    hasShadow: false
  }
}

/** 透明大圆角窗：同步清空色，并在 focus/blur 时重刷，避免 Win DWM 把四角画脏 */
function prepareFramelessWindow(win: BrowserWindow) {
  const refreshBg = () => {
    if (win.isDestroyed()) return
    win.setBackgroundColor(windowBackgroundColor())
  }
  refreshBg()
  win.on('focus', refreshBg)
  win.on('blur', refreshBg)
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

// Windows 控制台默认多为 GBK，Node 日志按 UTF-8 写出易乱码；尽量切到 UTF-8
if (process.platform === 'win32') {
  try {
    execSync('chcp 65001', { stdio: 'ignore' })
  } catch {
    /* ignore */
  }
}

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
// 注意：不要使用 --use-fake-device-for-media-stream，否则摄像头/麦克风无真实画面与声音

// [P2-8-5] 全局未捕获异常兜底：防止 Promise rejection / 同步异常导致进程崩溃或静默丢失
process.on('unhandledRejection', (reason) => {
  console.error('[electron] unhandledRejection:', reason)
})
process.on('uncaughtException', (err) => {
  console.error('[electron] uncaughtException:', err)
})

let mainWindow: BrowserWindow | null = null
let tray: Tray | null = null
/** 主动退出时跳过「关窗最小化到托盘」*/
let isQuitting = false
/** [P2-E3] 截图会话内授权记忆：首次用户确认后本次会话不再询问 */
let screenshotAllowed = false

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
  return {
    show: mainT(desktopPrefs.language, 'trayShow'),
    quit: mainT(desktopPrefs.language, 'trayQuit')
  }
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
  // ipinfo.io / ipapi.co：两者都返回 city + region 字段
  if (json.city && json.region) {
    return `${toCN(String(json.region))} ${toCN(String(json.city))}`.trim()
  }
  // ipapi.co：返回 country_name + region + city，无 region 时退化为 country + city
  if (json.city && json.country_name) {
    return `${toCN(String(json.country_name))} ${toCN(String(json.city))}`.trim()
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
          console.error('[Main] failed to parse IP location response:', e)
          resolve(null)
        }
      })
    })
    req.on('error', (e) => { console.error('[Main] IP location request error:', e.message); resolve(null) })
    req.on('timeout', () => { console.error('[Main] IP location request timeout'); req.destroy(); resolve(null) })
  })
}

function winFromSender(event: IpcMainEvent | IpcMainInvokeEvent): BrowserWindow | null {
  return BrowserWindow.fromWebContents(event.sender) ?? mainWindow
}

/** 图片预览独立窗口载荷（URL 可能很长，不走 hash query） */
type ImageViewerItem = {
  url: string
  fileName?: string
  fileSize?: string
  messageId?: string
  conversationId?: string
}

type ImageViewerPayload = {
  url?: string
  fileName?: string
  fileSize?: string
  items?: ImageViewerItem[]
  index?: number
}

let imageViewerWindow: BrowserWindow | null = null
let imageViewerPayload: ImageViewerPayload | null = null

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

const LOGIN_WINDOW_WIDTH = 319
const LOGIN_WINDOW_HEIGHT = 461
const MAIN_WINDOW_WIDTH = 1200
const MAIN_WINDOW_HEIGHT = 800
const WINDOW_MODE_ANIM_MS = 380

function easeOutCubic(t: number): number {
  return 1 - Math.pow(1 - t, 3)
}

function animateWindowSize(
  win: BrowserWindow,
  targetWidth: number,
  targetHeight: number,
  durationMs = WINDOW_MODE_ANIM_MS
): Promise<void> {
  const [startW, startH] = win.getSize()
  if (startW === targetWidth && startH === targetHeight) return Promise.resolve()

  return new Promise(resolve => {
    const startTime = Date.now()
    const tick = () => {
      if (win.isDestroyed()) {
        resolve()
        return
      }
      const elapsed = Date.now() - startTime
      const progress = Math.min(elapsed / durationMs, 1)
      const eased = easeOutCubic(progress)
      const w = Math.round(startW + (targetWidth - startW) * eased)
      const h = Math.round(startH + (targetHeight - startH) * eased)
      win.setSize(w, h, false)
      if (progress < 1) {
        setTimeout(tick, 16)
      } else {
        win.center()
        resolve()
      }
    }
    tick()
  })
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
  ipcMain.removeHandler('app:pick-images')
  ipcMain.removeHandler('app:open-download-path')
  ipcMain.removeHandler('app:clear-cache')
  ipcMain.removeHandler('app:download-file')
  ipcMain.removeHandler('app:download-and-install-update')
  ipcMain.removeHandler('app:set-shortcuts')
  ipcMain.removeHandler('app:get-shortcuts')
  ipcMain.removeHandler('app:get-download-path')
  ipcMain.removeHandler('screen:capture')
  ipcMain.removeHandler('clipboard:write-text')
  ipcMain.removeHandler('clipboard:write-image')
  ipcMain.removeHandler('shell:open-external')
  ipcMain.removeHandler('shell:open-path')
  ipcMain.removeHandler('image-viewer:get-payload')

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

  ipcMain.handle('window:set-mode', async (event, mode: 'login' | 'main') => {
    const win = winFromSender(event)
    if (!win) return
    if (mode === 'login') {
      if (win.isMaximized()) win.unmaximize()
      win.setMaximizable(false)
      win.setMaximumSize(99999, 99999)
      win.setMinimumSize(100, 100)
      win.setResizable(true)
      await animateWindowSize(win, LOGIN_WINDOW_WIDTH, LOGIN_WINDOW_HEIGHT)
      win.setResizable(false)
      win.setMinimumSize(LOGIN_WINDOW_WIDTH, LOGIN_WINDOW_HEIGHT)
      win.setMaximumSize(LOGIN_WINDOW_WIDTH, LOGIN_WINDOW_HEIGHT)
      win.center()
      return
    }
    // 登录窗创建时因 maxSize 锁定会关掉可最大化；切主界面需显式恢复
    win.setMaximumSize(99999, 99999)
    win.setMinimumSize(LOGIN_WINDOW_WIDTH, LOGIN_WINDOW_HEIGHT)
    win.setResizable(true)
    win.setMaximizable(true)
    if (!win.isMaximized()) {
      await animateWindowSize(win, MAIN_WINDOW_WIDTH, MAIN_WINDOW_HEIGHT)
      win.center()
    }
    win.setMinimumSize(MAIN_WINDOW_WIDTH, MAIN_WINDOW_HEIGHT)
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
      title: mainT(desktopPrefs.language, 'pickImagesTitle'),
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
    const downloadsRoot = app.getPath('downloads')
    // customPath 来自渲染进程，不可信：默认仅打开系统下载目录
    if (!customPath || !customPath.trim()) {
      const err = await shell.openPath(downloadsRoot)
      return !err
    }
    const target = path.resolve(customPath.trim())
    // [P1-E3] 路径穿越防护：仅允许打开 downloads 目录子树内的路径
    const rel = path.relative(downloadsRoot, target)
    if (rel.startsWith('..') || path.isAbsolute(rel)) {
      return false
    }
    // [P1-E3] 仅允许打开目录，拒绝文件类型，防止任意文件执行
    try {
      if (!fs.existsSync(target) || !fs.statSync(target).isDirectory()) {
        return false
      }
    } catch {
      return false
    }
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
      return { ok: true, message: mainT(desktopPrefs.language, 'cacheCleared') }
    } catch (e) {
      return {
        ok: false,
        message: e instanceof Error ? e.message : mainT(desktopPrefs.language, 'cacheClearFail')
      }
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

      // 聊天「打开文件」：保存后用系统默认程序打开（可执行类扩展名仅保存不自动打开）
      if (payload.openAfter) {
        if (isDangerousOpenExtension(targetPath)) {
          return {
            ok: true,
            path: targetPath,
            message: mainT(desktopPrefs.language, 'downloadSavedNoAutoOpen')
          }
        }
        const { shell } = await import('electron')
        const openErr = await shell.openPath(targetPath)
        if (openErr) {
          return { ok: true, path: targetPath, message: openErr }
        }
      }
      return { ok: true, path: targetPath }
    } catch (e) {
      return {
        ok: false,
        message: e instanceof Error ? e.message : mainT(desktopPrefs.language, 'downloadFail')
      }
    }
  })

  /**
   * 检查更新后的自动下载安装：下载安装包到临时目录并拉起系统安装程序。
   * Windows：默认静默安装（/S），安装完成后由 NSIS 脚本自动启动 LinkX。
   * 手动安装包仍走 NSIS 图形向导（可选路径、协议页、快捷方式）。
   *
   * 安全约束：
   * 1. 仅允许 HTTPS（杜绝明文中间人篡改）
   * 2. 域名必须在白名单内（默认 GitHub Releases，可通过 LINKX_UPDATE_HOSTS 扩展）
   * 3. 文件扩展名仅允许 .exe/.msi/.dmg/.AppImage（安装包）
   */
  ipcMain.handle(
    'app:download-and-install-update',
    async (
      event,
      payload: { url?: string; version?: string; fileName?: string; sha256?: string; silent?: boolean } = {}
    ) => {
      try {
        const url = (payload.url || '').trim()
        const isHttps = /^https:\/\//i.test(url)
        const isLocalHttp =
          /^http:\/\/(localhost|127\.0\.0\.1|\[::1\])(:\d+)?\//i.test(url) && isDev
        if (!isHttps && !isLocalHttp) {
          return { ok: false, message: mainT(desktopPrefs.language, 'downloadHttpsOnly') }
        }

        // 域名白名单校验，防止渲染进程被 XSS 后下载执行任意来源 exe
        let apiHost = ''
        try {
          apiHost = new URL(resolveApiBaseUrl(process.env.VITE_API_BASE_URL)).hostname.toLowerCase()
        } catch {
          apiHost = ''
        }
        const defaultHosts = ['github.com', 'objects.githubusercontent.com', apiHost]
          .filter(Boolean)
          .join(',')
        const allowedHosts = (process.env.LINKX_UPDATE_HOSTS || defaultHosts)
          .split(',')
          .map(h => h.trim().toLowerCase())
          .filter(Boolean)
        let parsedUrl: URL
        try {
          parsedUrl = new URL(url)
        } catch {
          return { ok: false, message: mainT(desktopPrefs.language, 'downloadInvalidUrl') }
        }
        if (!allowedHosts.includes(parsedUrl.hostname.toLowerCase())) {
          return { ok: false, message: mainT(desktopPrefs.language, 'downloadNotWhitelisted') }
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

        // 扩展名白名单：仅允许安装包格式，防止下载执行任意类型文件
        const allowedExts = ['.exe', '.msi', '.dmg', '.AppImage', '.deb', '.rpm']
        const ext = path.extname(fileName).toLowerCase()
        if (!allowedExts.includes(ext)) {
          return { ok: false, message: mainT(desktopPrefs.language, 'downloadInstallerOnly') }
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
          return {
            ok: false,
            message: mainT(desktopPrefs.language, 'downloadFailStatus', { status: res.status })
          }
        }
        const bytes = Buffer.from(await res.arrayBuffer())

        const expectedSha = (payload.sha256 || '').trim().toLowerCase()
        if (expectedSha) {
          const { createHash } = await import('crypto')
          const actualSha = createHash('sha256').update(bytes).digest('hex')
          if (actualSha !== expectedSha) {
            return { ok: false, message: mainT(desktopPrefs.language, 'downloadChecksumMismatch') }
          }
        }

        await fs.promises.writeFile(targetPath, bytes)

        win?.webContents.send('app:update-progress', { phase: 'installing', percent: 100 })

        const silent = payload.silent !== false
        const isWindowsInstaller = process.platform === 'win32' && (ext === '.exe' || ext === '.msi')
        let launched = false
        let silentInstall = false

        if (isWindowsInstaller && silent) {
          try {
            const child = spawn(targetPath, ['/S'], {
              detached: true,
              stdio: 'ignore',
              windowsHide: true
            })
            child.unref()
            launched = true
            silentInstall = true
          } catch (spawnErr) {
            console.warn('[update] 静默安装启动失败，回退到图形安装:', spawnErr)
          }
        }

        if (!launched) {
          const { shell } = await import('electron')
          const openErr = await shell.openPath(targetPath)
          if (openErr) {
            await shell.showItemInFolder(targetPath)
            return {
              ok: true,
              path: targetPath,
              launched: false,
              silent: false,
              message: mainT(desktopPrefs.language, 'downloadInstallerReady')
            }
          }
          launched = true
        }

        // 安装程序拉起后退出应用，避免文件占用导致覆盖失败
        setTimeout(() => {
          app.quit()
        }, silentInstall ? 400 : 800)

        return { ok: true, path: targetPath, launched, silent: silentInstall }
      } catch (e) {
        return {
          ok: false,
          message: e instanceof Error ? e.message : mainT(desktopPrefs.language, 'downloadInstallerFail')
        }
      }
    }
  )

  ipcMain.handle('app:get-shortcuts', () => ({ ...currentShortcuts }))

  ipcMain.handle('app:set-shortcuts', (_event, payload: { toggleWindow?: string; lock?: string }) => {
    // [P3-31] 校验快捷键格式，防止非法 Accelerator 导致注册异常或覆盖已有合法值
    if (payload?.toggleWindow !== undefined && !isValidAccelerator(payload.toggleWindow)) {
      console.warn('[shortcut] invalid toggleWindow accelerator:', payload.toggleWindow)
      return false
    }
    if (payload?.lock !== undefined && !isValidAccelerator(payload.lock)) {
      console.warn('[shortcut] invalid lock accelerator:', payload.lock)
      return false
    }
    if (payload?.toggleWindow) currentShortcuts.toggleWindow = String(payload.toggleWindow)
    if (payload?.lock) currentShortcuts.lock = String(payload.lock)
    return registerGlobalShortcuts()
  })

  // 通过 IP 获取地理位置（优先国内可访问接口，失败再回退）
  // [P2-8-4] 全部使用 HTTPS，避免明文 HTTP 被中间人篡改/窃听
  ipcMain.handle('fetch-ip-location', async () => {
    const services = [
      'https://ip9.com.cn/get',
      'https://ipinfo.io/json',
      'https://ipapi.co/json/'
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

  // [P2-E3] 屏幕截图：必须经用户确认后才调用 desktopCapturer，防止渲染进程静默截屏
  // 首次弹窗确认，确认后本次会话内不再询问
  ipcMain.handle('clipboard:write-text', (_event, text: string) => {
    clipboard.writeText(String(text ?? ''))
    return true
  })

  /** 写入图片到剪贴板：支持 dataURL 或 http(s) URL（主进程拉取，避开 CORS） */
  ipcMain.handle(
    'clipboard:write-image',
    async (_event, payload: { dataUrl?: string; url?: string } = {}) => {
      try {
        let img = nativeImage.createEmpty()
        const dataUrl = (payload.dataUrl || '').trim()
        if (dataUrl.startsWith('data:image/')) {
          img = nativeImage.createFromDataURL(dataUrl)
        } else {
          const url = (payload.url || '').trim()
          if (!/^https?:\/\//i.test(url)) return false
          const res = await net.fetch(url)
          if (!res.ok) return false
          const buf = Buffer.from(await res.arrayBuffer())
          img = nativeImage.createFromBuffer(buf)
        }
        if (img.isEmpty()) return false
        clipboard.writeImage(img)
        return true
      } catch {
        return false
      }
    }
  )

  ipcMain.handle('shell:open-external', async (_event, url: string) => {
    try {
      const target = String(url || '').trim()
      if (!/^https?:\/\//i.test(target) && !target.startsWith('file:')) return false
      const { shell } = await import('electron')
      await shell.openExternal(target)
      return true
    } catch {
      return false
    }
  })

  ipcMain.handle('shell:open-path', async (_event, filePath: string) => {
    try {
      const target = path.resolve(String(filePath || '').trim())
      if (!target || !fs.existsSync(target)) return false
      const { shell } = await import('electron')
      const err = await shell.openPath(target)
      return !err
    } catch {
      return false
    }
  })

  ipcMain.handle('image-viewer:get-payload', () => imageViewerPayload)

  ipcMain.handle('screen:capture', async (event) => {
    if (!screenshotAllowed) {
      const win = winFromSender(event) ?? mainWindow
      if (!win) return null
      const result = await dialog.showMessageBox(win, {
        type: 'question',
        buttons: [
          mainT(desktopPrefs.language, 'screenshotAllow'),
          mainT(desktopPrefs.language, 'screenshotCancel')
        ],
        defaultId: 1,
        title: mainT(desktopPrefs.language, 'screenshotTitle'),
        message: mainT(desktopPrefs.language, 'screenshotMessage'),
        detail: mainT(desktopPrefs.language, 'screenshotDetail')
      })
      if (result.response !== 0) return null
      screenshotAllowed = true
    }
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
      console.error('[Main] screenshot failed:', e)
      return null
    }
  })
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
        let showTimer: NodeJS.Timeout | null = null
        const done = (ok: boolean) => {
          if (settled) return
          settled = true
          if (showTimer) clearTimeout(showTimer)
          resolve(ok)
        }
        n.on('failed', (_e, err) => {
          console.warn('[Main] Notification failed:', err)
          done(false)
        })
        // 不信任 show：Windows 上可能 show 后又 failed
        n.show()
        // 800ms 兜底完成 Promise；failed 提前触发时 done 内清理 timer 避免事件循环残留
        showTimer = setTimeout(() => done(true), 800)
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

function applyAllWindowBackgrounds(theme: string) {
  const color = windowBackgroundColor(theme)
  BrowserWindow.getAllWindows().forEach(win => win.setBackgroundColor(color))
}

ipcMain.on('theme-changed', (_e, theme: string) => {
  if (theme === 'light' || theme === 'dark') {
    currentUiTheme = theme
  }
  applyAllWindowBackgrounds(theme)
})

/** 解析 build 目录下的应用资源（开发/打包路径兼容） */
function resolveBuildAsset(fileName: string): string | null {
  const roots = [
    path.join(app.getAppPath(), 'build'),
    path.join(process.resourcesPath, 'build'),
    path.join(__dirname, '../../build'),
    path.join(__dirname, '../build')
  ]
  for (const root of roots) {
    const candidate = path.join(root, fileName)
    try {
      if (fs.existsSync(candidate)) return candidate
    } catch {
      /* fall through */
    }
  }
  return null
}

function resolveAppIconPath(): string | undefined {
  const preferred =
    process.platform === 'win32'
      ? 'icon.ico'
      : process.platform === 'darwin'
        ? 'icon.icns'
        : 'icon.png'
  return resolveBuildAsset(preferred) ?? resolveBuildAsset('icon.png') ?? undefined
}

function createAppIconImage(): Electron.NativeImage | undefined {
  // Windows 任务栏/窗口图标：优先 PNG（透明圆角），.ico 在部分 DPI 下圆角不明显
  if (process.platform === 'win32') {
    for (const file of ['icon-256.png', 'icon-128.png', 'icon.png']) {
      const pngPath = resolveBuildAsset(file)
      if (!pngPath) continue
      const pngImg = nativeImage.createFromPath(pngPath)
      if (!pngImg.isEmpty()) return pngImg
    }
  }

  const iconPath = resolveAppIconPath()
  if (!iconPath) return undefined
  const img = nativeImage.createFromPath(iconPath)
  return img.isEmpty() ? undefined : img
}

function browserWindowIconOptions(): Pick<Electron.BrowserWindowConstructorOptions, 'icon'> {
  const icon = createAppIconImage()
  return icon ? { icon } : {}
}

/** 托盘图标：专用 icon-tray.png（边距更小），HiDPI 下缩放到 32px 更清晰 */
function createTrayIcon(): Electron.NativeImage {
  const trayPath =
    resolveBuildAsset('icon-tray.png') ??
    resolveBuildAsset('icon-64.png') ??
    resolveBuildAsset('icon-48.png') ??
    resolveBuildAsset('icon-32.png') ??
    resolveBuildAsset('icon.png')
  if (trayPath) {
    try {
      const img = nativeImage.createFromPath(trayPath)
      if (!img.isEmpty()) {
        const size = img.getSize()
        if (size.width > 32 || size.height > 32) {
          return img.resize({ width: 32, height: 32, quality: 'best' })
        }
        return img
      }
    } catch {
      /* fall through */
    }
  }
  // 回退：16x16 纯蓝圆点，保证最小可用托盘体验
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

/**
 * 校验 Electron Accelerator 快捷键格式
 * 空字符串或 null 表示"禁用快捷键"，允许通过（不注册）
 * 非空字符串必须符合 Electron Accelerator 规范：修饰键(可多个) + 普通键
 */
function isValidAccelerator(accelerator: string | null | undefined): boolean {
  // 空值表示禁用快捷键，允许通过
  if (!accelerator) return true
  // 注意：CommandOrControl 必须写在 Command 之前，否则会被 Command 前缀误匹配
  const mod =
    'CommandOrControl|CmdOrCtrl|Command|Ctrl|Alt|Option|Shift|Super|Meta'
  const key =
    '[A-Za-z0-9]|F[1-9]|F1[0-9]|F2[0-4]|Space|Tab|Enter|Return|Up|Down|Left|Right|Home|End|PageUp|PageDown|Escape|Esc|Backspace|Delete|Insert|VolumeUp|VolumeDown|VolumeMute|MediaNextTrack|MediaPreviousTrack|MediaPlayPause|MediaStop|Plus|='
  const acceleratorRegex = new RegExp(`^(${mod})(\\+(${mod}))*\\+(${key})$`)
  return acceleratorRegex.test(accelerator)
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

type MomentsOpenPayload = { userId?: string; name?: string }

function buildMomentsHash(opts?: MomentsOpenPayload): string {
  if (opts?.userId) {
    const params = new URLSearchParams()
    params.set('userId', String(opts.userId))
    if (opts.name) params.set('name', String(opts.name))
    return `/moments?${params.toString()}`
  }
  return '/moments'
}

function loadMomentsHash(win: BrowserWindow, hashPath: string) {
  if (isDev && process.env.VITE_DEV_SERVER_URL) {
    win.loadURL(process.env.VITE_DEV_SERVER_URL + '#' + hashPath)
  } else {
    win.loadFile(path.join(__dirname, '../../dist/index.html'), { hash: hashPath })
  }
}

function createMomentsWindow(opts?: MomentsOpenPayload) {
  const hashPath = buildMomentsHash(opts)

  if (momentsWindow && !momentsWindow.isDestroyed()) {
    if (momentsWindow.isMinimized()) momentsWindow.restore()
    momentsWindow.focus()
    // 已打开时切换到目标用户友链（或回到总览）
    const hash = '#' + hashPath
    momentsWindow.webContents
      .executeJavaScript(`window.location.hash = ${JSON.stringify(hash)}`)
      .catch(() => loadMomentsHash(momentsWindow!, hashPath))
    return
  }

  momentsWindow = new BrowserWindow({
    ...browserWindowIconOptions(),
    width: 500,
    height: 720,
    minWidth: 440,
    minHeight: 560,
    resizable: true,
    ...framelessChrome(),
    show: false,
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  })
  prepareFramelessWindow(momentsWindow)

  momentsWindow.once('ready-to-show', () => {
    momentsWindow?.show()
  })

  loadMomentsHash(momentsWindow, hashPath)

  momentsWindow.on('closed', () => {
    momentsWindow = null
  })
}

ipcMain.on('window-open-moments', (_event, payload?: MomentsOpenPayload) => {
  const opts =
    payload && typeof payload === 'object' && (payload.userId || payload.name)
      ? {
          userId: payload.userId ? String(payload.userId) : undefined,
          name: payload.name ? String(payload.name) : undefined
        }
      : undefined
  createMomentsWindow(opts)
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
    ...browserWindowIconOptions(),
    width: 420,
    height: 520,
    resizable: false,
    ...framelessChrome(),
    show: false,
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  })
  prepareFramelessWindow(momentsTextWindow)

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
    ...browserWindowIconOptions(),
    width: 480,
    height: 600,
    resizable: false,
    ...framelessChrome(),
    show: false,
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  })
  prepareFramelessWindow(momentsMediaWindow)

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
  if (noteEditorWindow && !noteEditorWindow.isDestroyed()) {
    if (noteEditorWindow.isMinimized()) noteEditorWindow.restore()
    if (!noteEditorWindow.isVisible()) noteEditorWindow.show()
    noteEditorWindow.focus()
    noteEditorWindow.webContents.send('note-editor:reset')
    return
  }

  noteEditorWindow = new BrowserWindow({
    ...browserWindowIconOptions(),
    width: 800,
    height: 600,
    minWidth: 600,
    minHeight: 400,
    ...framelessChrome(),
    show: false,
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  })
  prepareFramelessWindow(noteEditorWindow)

  let revealed = false
  const revealNoteEditorWindow = () => {
    if (revealed || !noteEditorWindow || noteEditorWindow.isDestroyed()) return
    revealed = true
    noteEditorWindow.show()
    noteEditorWindow.focus()
  }

  noteEditorWindow.once('ready-to-show', revealNoteEditorWindow)
  noteEditorWindow.webContents.once('did-finish-load', () => {
    // ready-to-show 偶发不触发时，确保窗口仍能显示
    setTimeout(revealNoteEditorWindow, 0)
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
    ...browserWindowIconOptions(),
    width: 360,
    height: 560,
    resizable: false,
    ...framelessChrome(),
    show: false,
    // 不挂 parent，避免盖住登录窗；作为独立弹窗并列显示
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  })
  prepareFramelessWindow(registerWindow)

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

/**
 * 聊天记录管理独立窗口（帮助文档已改为浏览器打开线上地址）。
 */
function createChatHistoryStandaloneWindow(size: { width: number; height: number }) {
  const stateRef = chatHistoryWindowState
  let win = stateRef.win
  if (win && !win.isDestroyed()) {
    if (win.isMinimized()) win.restore()
    win.focus()
    return
  }

  win = new BrowserWindow({
    ...browserWindowIconOptions(),
    width: size.width,
    height: size.height,
    minWidth: size.width,
    minHeight: size.height,
    resizable: true,
    ...framelessChrome(),
    show: false,
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  })
  prepareFramelessWindow(win)

  win.once('ready-to-show', () => {
    win?.show()
  })

  if (isDev && process.env.VITE_DEV_SERVER_URL) {
    win.loadURL(process.env.VITE_DEV_SERVER_URL + '#/chat-history')
  } else {
    win.loadFile(path.join(__dirname, '../../dist/index.html'), { hash: '/chat-history' })
  }

  win.on('closed', () => {
    stateRef.win = null
  })

  stateRef.win = win
}

interface StandaloneWindowState {
  win: BrowserWindow | null
}

const chatHistoryWindowState: StandaloneWindowState = { win: null }
const officialNotifyDetailWindowState: StandaloneWindowState = { win: null }

const HELP_PAGE_BASE_URL = resolveHelpPageBaseUrl(process.env.VITE_HELP_PAGE_BASE_URL)

ipcMain.on('window-open-help', () => {
  const url = buildHelpPageUrl(desktopPrefs.language, undefined, HELP_PAGE_BASE_URL)
  void shell.openExternal(url)
})

ipcMain.on('window-open-chat-history', () => {
  createChatHistoryStandaloneWindow({ width: 820, height: 760 })
})

ipcMain.on('window-open-official-notify-detail', (_event, notifId: unknown) => {
  const id = String(notifId ?? '').trim()
  if (!id) return
  createOfficialNotifyDetailWindow(id)
})

function createOfficialNotifyDetailWindow(notifId: string) {
  const hashPath = `official-notify/${encodeURIComponent(notifId)}`
  let win = officialNotifyDetailWindowState.win
  if (win && !win.isDestroyed()) {
    if (isDev && process.env.VITE_DEV_SERVER_URL) {
      win.loadURL(process.env.VITE_DEV_SERVER_URL + '#/' + hashPath)
    } else {
      win.loadFile(path.join(__dirname, '../../dist/index.html'), { hash: '/' + hashPath })
    }
    if (win.isMinimized()) win.restore()
    win.focus()
    return
  }

  win = new BrowserWindow({
    ...browserWindowIconOptions(),
    width: 520,
    height: 680,
    minWidth: 400,
    minHeight: 480,
    resizable: true,
    ...framelessChrome(),
    show: false,
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  })
  prepareFramelessWindow(win)

  win.once('ready-to-show', () => {
    win?.show()
  })

  if (isDev && process.env.VITE_DEV_SERVER_URL) {
    win.loadURL(process.env.VITE_DEV_SERVER_URL + '#/' + hashPath)
  } else {
    win.loadFile(path.join(__dirname, '../../dist/index.html'), { hash: '/' + hashPath })
  }

  win.on('closed', () => {
    officialNotifyDetailWindowState.win = null
  })

  officialNotifyDetailWindowState.win = win
}

function createImageViewerWindow(payload: ImageViewerPayload) {
  const url = (payload.url || '').trim()
  const rawItems = Array.isArray(payload.items) ? payload.items : undefined
  const hasResolvableItem = rawItems?.some(
    i => i && ((typeof i.url === 'string' && i.url.trim()) || i.messageId)
  )
  if (!url && !hasResolvableItem) return

  const items = rawItems
    ? rawItems
        .filter(
          i =>
            i &&
            ((typeof i.url === 'string' && i.url.trim()) ||
              (i.messageId != null && String(i.messageId).trim()))
        )
        .map(i => ({
          url: typeof i.url === 'string' ? i.url.trim() : '',
          fileName: i.fileName ? String(i.fileName) : undefined,
          fileSize: i.fileSize ? String(i.fileSize) : undefined,
          messageId: i.messageId ? String(i.messageId) : undefined,
          conversationId: i.conversationId ? String(i.conversationId) : undefined
        }))
    : undefined

  const resolvedUrl =
    url ||
    (typeof payload.index === 'number' && items?.[payload.index]?.url) ||
    items?.[0]?.url ||
    ''

  imageViewerPayload = {
    url: resolvedUrl,
    fileName: payload.fileName ? String(payload.fileName) : undefined,
    fileSize: payload.fileSize ? String(payload.fileSize) : undefined,
    items,
    index: typeof payload.index === 'number' ? payload.index : 0
  }

  if (imageViewerWindow && !imageViewerWindow.isDestroyed()) {
    if (imageViewerWindow.isMinimized()) imageViewerWindow.restore()
    imageViewerWindow.focus()
    imageViewerWindow.webContents.send('image-viewer:payload', imageViewerPayload)
    return
  }

  imageViewerWindow = new BrowserWindow({
    ...browserWindowIconOptions(),
    width: 960,
    height: 720,
    minWidth: 640,
    minHeight: 480,
    resizable: true,
    ...framelessChrome(),
    // 跟随当前 UI 主题，避免亮色主题下先闪黑底
    backgroundColor: currentUiTheme === 'dark' ? '#1a1a1a' : '#f5f5f5',
    show: false,
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  })
  prepareFramelessWindow(imageViewerWindow)

  imageViewerWindow.once('ready-to-show', () => {
    imageViewerWindow?.show()
  })

  if (isDev && process.env.VITE_DEV_SERVER_URL) {
    imageViewerWindow.loadURL(process.env.VITE_DEV_SERVER_URL + '#/image-viewer')
  } else {
    imageViewerWindow.loadFile(path.join(__dirname, '../../dist/index.html'), {
      hash: '/image-viewer'
    })
  }

  imageViewerWindow.on('closed', () => {
    imageViewerWindow = null
    imageViewerPayload = null
  })
}

ipcMain.on('window-open-image-viewer', (_event, payload?: ImageViewerPayload) => {
  if (!payload || typeof payload !== 'object') return
  createImageViewerWindow(payload)
})

/** [P1-E2] 判断 URL 是否为应用自身源（开发环境为 Vite Dev Server，生产环境为 file://） */
function isSelfOrigin(url: string): boolean {
  try {
    const parsed = new URL(url)
    const devUrl = process.env.VITE_DEV_SERVER_URL
    if (devUrl) {
      try {
        return parsed.origin === new URL(devUrl).origin
      } catch {
        return false
      }
    }
    // 生产环境打包后加载本地 file:// 资源
    return parsed.protocol === 'file:'
  } catch {
    return false
  }
}

function createWindow() {
  if (isDev) {
    console.log('[electron] preload:', preloadPath, 'exists:', fs.existsSync(preloadPath))
  }

  mainWindow = new BrowserWindow({
    ...browserWindowIconOptions(),
    width: 319,
    height: 461,
    minWidth: 319,
    minHeight: 461,
    maxWidth: 319,
    maxHeight: 461,
    resizable: false,
    ...framelessChrome(),
    show: false,
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webviewTag: false
    }
  })
  prepareFramelessWindow(mainWindow)

  let mainWindowRevealed = false
  const revealMainWindow = () => {
    if (mainWindowRevealed || !mainWindow || mainWindow.isDestroyed()) return
    if (desktopPrefs.openOnStartup === 'tray') return
    mainWindowRevealed = true
    mainWindow.show()
  }

  mainWindow.once('ready-to-show', revealMainWindow)
  // 开发态 Vite 首包较慢：ready-to-show 可能早于 JS 执行，补一次 did-finish-load 展示
  mainWindow.webContents.once('did-finish-load', () => {
    setTimeout(revealMainWindow, 0)
  })

  if (isDev && process.env.VITE_DEV_SERVER_URL) {
    mainWindow.loadURL(process.env.VITE_DEV_SERVER_URL)
  } else {
    mainWindow.loadFile(path.join(__dirname, '../../dist/index.html'))
  }

  // [P1-E1] 拒绝所有 window.open 新窗口，防止渲染进程被 XSS 后弹出恶意页面
  mainWindow.webContents.setWindowOpenHandler(() => ({ action: 'deny' }))

  // [P1-E2] 限制页面导航：仅允许同源或 file:// 内部导航，阻止跳转到外部站点
  mainWindow.webContents.on('will-navigate', (e, url) => {
    if (url.startsWith('http') && !isSelfOrigin(url)) {
      e.preventDefault()
    }
  })

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
          console.error('[electron] window.electronAPI missing, preload:', preloadPath)
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
  const appIcon = createAppIconImage()
  if (appIcon && process.platform === 'darwin' && app.dock) {
    app.dock.setIcon(appIcon)
  }

  // Windows/Linux：去掉经典 File/Edit 菜单栏
  if (process.platform !== 'darwin') {
    Menu.setApplicationMenu(null)
  }

  desktopPrefs = loadDesktopPrefs()

  // 允许本应用使用摄像头/麦克风（替代假设备开关，保证真实音视频）
  // [P3-30] display-capture 不在此放行：屏幕共享走专门的 screen:capture IPC + dialog 确认，
  // 避免任意页面通过 setPermissionRequestHandler 静默获取屏幕采集权限。
  const allowMediaPermissions = new Set([
    'media',
    'microphone',
    'camera',
    'geolocation',
    'notifications'
  ])
  session.defaultSession.setPermissionRequestHandler((_wc, permission, callback) => {
    callback(allowMediaPermissions.has(permission))
  })
  session.defaultSession.setPermissionCheckHandler((_wc, permission) => {
    return allowMediaPermissions.has(permission)
  })

  // 设置严格的 Content Security Policy，防止 Electron Security Warning
  // 在窗口创建前设置，应用到所有窗口
  // img-src / media-src：仅本应用、本机 MinIO/API，以及可选的生产媒体源（不再放开任意 http/https）
  const mediaOrigins = collectTrustedMediaOrigins()
  const connectOrigins = connectOriginsForCsp(
    resolveApiBaseUrl(process.env.VITE_API_BASE_URL),
    resolveWsBaseUrl(process.env.VITE_WS_BASE_URL)
  )
  // [P2-8-2] CSP 策略强化：
  // - dev mode：Vite HMR 需要内联脚本，保留 'unsafe-inline'
  // - prod mode：构建产物无内联脚本，移除 script-src 'unsafe-inline' 降低 XSS 风险
  // - style-src 保留 'unsafe-inline'：Vue 运行时 + Naive UI 动态注入样式必需
  const scriptSrc = isDev ? "script-src 'self' 'unsafe-inline';" : "script-src 'self';"
  const csp = [
    "default-src 'self';",
    "base-uri 'self';",
    "object-src 'none';",
    "frame-ancestors 'none';",
    "form-action 'self';",
    scriptSrc,
    "style-src 'self' 'unsafe-inline';",
    `img-src 'self' data: blob: ${mediaOrigins};`,
    "font-src 'self' data:;",
    `connect-src 'self' ${connectOrigins};`,
    `media-src 'self' blob: mediastream: ${mediaOrigins};`
  ].join(' ')

  app.on('web-contents-created', (_event, contents) => {
    contents.session.webRequest.onHeadersReceived((details, callback) => {
      callback({
        responseHeaders: {
          ...details.responseHeaders,
          'Content-Security-Policy': [csp],
          // 与 index.html meta 对齐，降低外链 CDN 防盗链 403
          'Referrer-Policy': ['no-referrer']
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
