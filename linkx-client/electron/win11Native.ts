/**
 * Windows 11 原生壳能力：任务栏徽章、进度、闪动、跳转列表、通话缩略图工具栏等。
 */
import { app, nativeImage, type BrowserWindow, type NativeImage } from 'electron'
import { Buffer } from 'node:buffer'
import type { MainLocale } from './mainI18n'
import { mainT } from './mainI18n'

export const JUMP_ARG_PREFIX = '--lx-jump='

export type JumpListAction = 'open' | 'chat' | 'contacts' | 'calendar' | 'settings'

export type DesktopNotificationAction = {
  kind: 'session' | 'official' | 'contacts' | 'calendar' | 'focus'
  sessionId?: string
  notificationId?: string
  avatarUrl?: string
}

export type CallToolbarPhase = 'incoming' | 'outgoing' | 'connecting' | 'connected' | 'idle'

export function parseJumpListArgv(argv: string[]): JumpListAction | null {
  for (const arg of argv) {
    if (!arg.startsWith(JUMP_ARG_PREFIX)) continue
    const action = arg.slice(JUMP_ARG_PREFIX.length)
    if (
      action === 'open' ||
      action === 'chat' ||
      action === 'contacts' ||
      action === 'calendar' ||
      action === 'settings'
    ) {
      return action
    }
  }
  return null
}

/** Win11 聚焦边框使用系统强调色时会出现蓝框，关闭以贴近原生扁平风格 */
export function disableWinAccentBorder(win: BrowserWindow): void {
  if (process.platform !== 'win32') return
  try {
    win.setAccentColor(false)
  } catch {
    /* ignore */
  }
}

function createCircleIcon(r: number, g: number, b: number, size = 16): NativeImage {
  const canvas = Buffer.alloc(size * size * 4)
  const cx = size / 2 - 0.5
  const cy = size / 2 - 0.5
  const radius = size / 2 - 1
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const i = (y * size + x) * 4
      const inCircle = (x - cx) ** 2 + (y - cy) ** 2 <= radius ** 2
      if (inCircle) {
        canvas[i] = r
        canvas[i + 1] = g
        canvas[i + 2] = b
        canvas[i + 3] = 255
      }
    }
  }
  return nativeImage.createFromBuffer(canvas, { width: size, height: size })
}

const badgeOverlayCache = new Map<string, NativeImage>()
const greenToolbarIcon = createCircleIcon(16, 185, 129)
const redToolbarIcon = createCircleIcon(239, 68, 68)

/** Win11 任务栏角标：右上角蓝色圆点（与系统/微信风格一致） */
function createCornerBadgeDot(): NativeImage {
  const size = 16
  const canvas = Buffer.alloc(size * size * 4)
  const cx = size - 4.5
  const cy = 4.5
  const radius = 3.5
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const i = (y * size + x) * 4
      const inCircle = (x - cx) ** 2 + (y - cy) ** 2 <= radius ** 2
      if (inCircle) {
        canvas[i] = 18
        canvas[i + 1] = 83
        canvas[i + 2] = 245
        canvas[i + 3] = 255
      }
    }
  }
  return nativeImage.createFromBuffer(canvas, { width: size, height: size })
}

export function createTaskbarBadgeOverlay(count: number): NativeImage {
  if (count <= 0) return nativeImage.createEmpty()
  const key = count > 99 ? '99+' : 'dot'
  const cached = badgeOverlayCache.get(key)
  if (cached) return cached
  const img = createCornerBadgeDot()
  badgeOverlayCache.set(key, img)
  return img
}

export function setTaskbarOverlayBadge(win: BrowserWindow | null | undefined, count: number): void {
  if (!win || win.isDestroyed()) return
  if (process.platform !== 'win32') return
  try {
    const overlay = createTaskbarBadgeOverlay(count)
    const description = count > 0 ? String(count) : ''
    win.setOverlayIcon(overlay, description)
  } catch {
    /* ignore */
  }
}

export function setTaskbarProgress(win: BrowserWindow | null | undefined, progress: number): void {
  if (!win || win.isDestroyed()) return
  if (process.platform !== 'win32') return
  try {
    if (progress < 0) {
      win.setProgressBar(-1)
    } else {
      win.setProgressBar(Math.min(1, Math.max(0, progress)))
    }
  } catch {
    /* ignore */
  }
}

export function flashWindowFrame(win: BrowserWindow | null | undefined, flash = true): void {
  if (!win || win.isDestroyed()) return
  try {
    if (flash && win.isFocused()) return
    win.flashFrame(flash)
  } catch {
    /* ignore */
  }
}

export function syncCallThumbnailToolbar(
  win: BrowserWindow | null | undefined,
  phase: CallToolbarPhase,
  lang: MainLocale,
  onAction: (action: 'accept' | 'reject' | 'hangup') => void
): void {
  if (!win || win.isDestroyed()) return
  if (process.platform !== 'win32') return

  if (phase === 'idle') {
    try {
      win.setThumbnailToolBar([])
    } catch {
      /* ignore */
    }
    return
  }

  const buttons: Electron.ThumbnailButton[] = []
  if (phase === 'incoming') {
    buttons.push({
      icon: greenToolbarIcon,
      click: () => onAction('accept'),
      tooltip: mainT(lang, 'toolbarAccept'),
      flags: ['enabled']
    })
    buttons.push({
      icon: redToolbarIcon,
      click: () => onAction('reject'),
      tooltip: mainT(lang, 'toolbarReject'),
      flags: ['enabled']
    })
  } else {
    buttons.push({
      icon: redToolbarIcon,
      click: () => onAction('hangup'),
      tooltip: mainT(lang, 'toolbarHangup'),
      flags: ['enabled']
    })
  }

  try {
    win.setThumbnailToolBar(buttons)
  } catch {
    /* ignore */
  }
}

export function clearWinJumpList(): void {
  if (process.platform !== 'win32') return
  try {
    app.setJumpList([])
  } catch {
    /* ignore */
  }
}

/** @deprecated 使用 clearWinJumpList；Jump List 任务在开发态易误启 electron.exe */
export function configureWinJumpList(_lang: MainLocale, _iconPath: string | undefined): void {
  clearWinJumpList()
}

/** 带进度回调的 HTTPS 下载（用于更新包 / 大文件） */
export async function fetchUrlToFileWithProgress(
  url: string,
  onProgress: (ratio: number) => void
): Promise<Buffer> {
  const { net } = await import('electron')
  const res = await net.fetch(url)
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`)
  }

  const total = Number(res.headers.get('content-length') || 0)
  const reader = res.body?.getReader()

  if (!reader) {
    const bytes = Buffer.from(await res.arrayBuffer())
    onProgress(1)
    return bytes
  }

  const chunks: Buffer[] = []
  let received = 0
  onProgress(0)

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    const buf = Buffer.from(value)
    chunks.push(buf)
    received += buf.length
    const ratio = total > 0 ? received / total : received > 0 ? 0.5 : 0
    onProgress(Math.min(1, ratio))
  }

  const bytes = Buffer.concat(chunks)
  onProgress(1)
  return bytes
}
