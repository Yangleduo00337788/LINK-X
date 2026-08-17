/**
 * 托盘消息提醒（QQ 风格）：收到消息时托盘图标闪烁，仅鼠标移入图标才展示气泡，移出即隐藏。
 */
import { BrowserWindow, nativeImage, screen, type NativeImage, type Tray } from 'electron'
import { Buffer } from 'node:buffer'
import type { DesktopNotificationAction } from './win11Native'

export type TrayMessagePayload = {
  title: string
  body: string
  avatarUrl?: string
  unreadCount?: number
  action?: DesktopNotificationAction
}

type TrayNotifyDeps = {
  isDev: boolean
  devServerUrl?: string
  distHtml: string
  defaultWebPreferences: () => Electron.WebPreferences
  getTrayIcon: () => NativeImage
  getTrayFlashIcon: () => NativeImage
  getWindowBackgroundColor?: () => string
  onOpen: (action?: DesktopNotificationAction) => void
}

let trayNotifyDeps: TrayNotifyDeps | null = null
let trayRef: Tray | null = null
let trayMessageWindow: BrowserWindow | null = null
let trayMessageReady = false
let pendingTrayPayload: TrayMessagePayload | null = null
let trayUnreadTotal = 0
let hoveringTrayIcon = false
let hoveringPopup = false

let trayFlashTimer: ReturnType<typeof setInterval> | null = null
let trayFlashOn = false
let hideTrayPopupTimer: ReturnType<typeof setTimeout> | null = null
let hoverWatchTimer: ReturnType<typeof setInterval> | null = null
let emptyTrayIcon: NativeImage | null = null
let transparentTrayIcon: NativeImage | null = null
let pendingShowTrayPopup = false
let hoverShowTimer: ReturnType<typeof setTimeout> | null = null
let suppressHoverPopupUntil = 0
let trayMouseInside = false
let trayPopupWindowGeneration = 0

const TRAY_POPUP_WIDTH = 312
const TRAY_POPUP_HEIGHT = 140
const TRAY_POPUP_WINDOW_GENERATION = 5
const HOVER_SHOW_DELAY_MS = 300
const CLICK_SUPPRESS_MS = 800

export function initTrayNotifyWindow(deps: TrayNotifyDeps) {
  trayNotifyDeps = deps
}

export function bindTrayForNotify(tray: Tray) {
  trayRef = tray
  tray.removeAllListeners('mouse-enter')
  tray.removeAllListeners('mouse-leave')
  tray.removeAllListeners('click')
  tray.on('mouse-enter', () => {
    trayMouseInside = true
    hoveringTrayIcon = true
    cancelHideTrayPopup()
    if (!pendingTrayPayload || !trayRef || trayRef.isDestroyed()) return
    const cursor = screen.getCursorScreenPoint()
    const bounds = trayRef.getBounds()
    if (isHoveringOverflowChevron(cursor, bounds)) return
    scheduleHoverShowPopup(true)
  })
  tray.on('mouse-leave', () => {
    trayMouseInside = false
    hoveringTrayIcon = false
    cancelHoverShowTimer()
    scheduleHideTrayPopup()
  })
  tray.on('click', () => {
    suppressHoverPopupAfterTrayClick()
  })
}

function cancelHoverShowTimer() {
  if (hoverShowTimer) {
    clearTimeout(hoverShowTimer)
    hoverShowTimer = null
  }
}

function scheduleHoverShowPopup(force = false) {
  if (!force && hoverShowTimer) return
  cancelHoverShowTimer()
  if (!pendingTrayPayload) return
  if (Date.now() < suppressHoverPopupUntil) return
  hoverShowTimer = setTimeout(() => {
    hoverShowTimer = null
    if (Date.now() < suppressHoverPopupUntil) return
    if (!pendingTrayPayload) return
    if (!shouldShowTrayHoverPreview()) return
    showTrayMessagePopup()
  }, HOVER_SHOW_DELAY_MS)
}

function shouldShowTrayHoverPreview(): boolean {
  return trayMouseInside || hoveringTrayIcon || isCursorOnTrayIcon()
}

function suppressHoverPopupAfterTrayClick() {
  suppressHoverPopupUntil = Date.now() + CLICK_SUPPRESS_MS
  cancelHoverShowTimer()
  pendingShowTrayPopup = false
  trayMouseInside = false
  hoveringTrayIcon = false
  hideTrayMessagePopup()
}

/** 收到消息：仅排队 + 闪烁托盘图标，绝不自动弹出气泡 */
export function queueTrayMessage(payload: TrayMessagePayload): void {
  if (!trayNotifyDeps) return
  trayUnreadTotal += 1
  pendingTrayPayload = {
    ...payload,
    unreadCount: trayUnreadTotal
  }
  prefetchTrayMessageWindow()
  startTrayIconFlash()
  startHoverWatch()
}

export function clearTrayMessageQueue(): void {
  trayUnreadTotal = 0
  pendingTrayPayload = null
  pendingShowTrayPopup = false
  cancelHoverShowTimer()
  stopTrayIconFlash()
  stopHoverWatch()
  hideTrayMessagePopup()
}

function getTransparentTrayIcon(): NativeImage {
  if (transparentTrayIcon && !transparentTrayIcon.isEmpty()) return transparentTrayIcon
  const size = 32
  const canvas = Buffer.alloc(size * size * 4, 0)
  transparentTrayIcon = nativeImage.createFromBuffer(canvas, { width: size, height: size })
  return transparentTrayIcon
}

function getEmptyTrayIcon(): NativeImage {
  if (emptyTrayIcon && !emptyTrayIcon.isEmpty()) return emptyTrayIcon
  emptyTrayIcon = nativeImage.createEmpty()
  return emptyTrayIcon
}

function startTrayIconFlash() {
  if (!trayRef || trayRef.isDestroyed() || !trayNotifyDeps) return
  if (trayFlashTimer) return
  const normal = trayNotifyDeps.getTrayIcon()
  const hidden = getTransparentTrayIcon()
  trayFlashOn = false
  trayFlashTimer = setInterval(() => {
    if (!trayRef || trayRef.isDestroyed()) return
    trayFlashOn = !trayFlashOn
    trayRef.setImage(trayFlashOn ? hidden : normal)
  }, 450)
}

function stopTrayIconFlash() {
  if (trayFlashTimer) {
    clearInterval(trayFlashTimer)
    trayFlashTimer = null
  }
  trayFlashOn = false
  if (trayRef && !trayRef.isDestroyed() && trayNotifyDeps) {
    trayRef.setImage(trayNotifyDeps.getTrayIcon())
  }
}

/** 有未读时轮询：溢出浮层内 getBounds 不可靠，配合 mouse-enter 触发悬停预览 */
function startHoverWatch() {
  if (hoverWatchTimer) return
  hoverWatchTimer = setInterval(() => {
    if (!pendingTrayPayload) {
      stopHoverWatch()
      return
    }
    if (hoveringPopup) return

    if (shouldShowTrayHoverPreview()) {
      cancelHideTrayPopup()
      scheduleHoverShowPopup()
      return
    }

    const win = trayMessageWindow
    if (win && !win.isDestroyed() && win.isVisible()) {
      scheduleHideTrayPopup()
    }
  }, 80)
}

function stopHoverWatch() {
  if (hoverWatchTimer) {
    clearInterval(hoverWatchTimer)
    hoverWatchTimer = null
  }
}

function isTrayBoundsInOverflowFlyout(bounds: Electron.Rectangle): boolean {
  const display = screen.getDisplayNearestPoint({
    x: bounds.x + bounds.width / 2,
    y: bounds.y + bounds.height / 2
  })
  const taskbarTop = display.workArea.y + display.workArea.height
  // 浮层图标整体在任务栏上沿之上；主托盘图标会压进任务栏条带
  return bounds.y + bounds.height <= taskbarTop
}

function getTrayIconHitRect(): Electron.Rectangle | null {
  if (!trayRef || trayRef.isDestroyed()) return null
  const b = trayRef.getBounds()
  if (b.width <= 0 || b.height <= 0) return null

  if (isTrayBoundsInOverflowFlyout(b)) {
    const pad = 12
    return {
      x: b.x - pad,
      y: b.y - pad,
      width: b.width + pad * 2,
      height: b.height + pad * 2
    }
  }

  const iconW = Math.min(b.width, 22)
  const iconH = Math.min(b.height, 22)
  const cx = b.x + b.width / 2
  const cy = b.y + b.height / 2
  return {
    x: Math.round(cx - iconW / 2),
    y: Math.round(cy - iconH / 2),
    width: iconW,
    height: iconH
  }
}

/** Win 隐藏托盘图标时 getBounds 会误报到「显示隐藏图标」箭头区域 */
function isHoveringOverflowChevron(cursor: Electron.Point, trayBounds: Electron.Rectangle): boolean {
  if (process.platform !== 'win32') return false

  const display = screen.getDisplayNearestPoint(cursor)
  const taskbarTop = display.workArea.y + display.workArea.height
  if (trayBounds.y < taskbarTop - 4) return false
  if (cursor.y < taskbarTop - 4) return false

  const chevronWidth = 24
  if (trayBounds.width > chevronWidth + 4) return false

  const chevronCenterX = trayBounds.x + trayBounds.width / 2
  return Math.abs(cursor.x - chevronCenterX) <= chevronWidth / 2
}

function isCursorOnTrayIcon(): boolean {
  const hit = getTrayIconHitRect()
  if (!hit || !trayRef || trayRef.isDestroyed()) return false

  const cursor = screen.getCursorScreenPoint()
  const bounds = trayRef.getBounds()
  const onIcon =
    cursor.x >= hit.x &&
    cursor.x <= hit.x + hit.width &&
    cursor.y >= hit.y &&
    cursor.y <= hit.y + hit.height
  if (!onIcon) return false

  if (!isTrayBoundsInOverflowFlyout(bounds) && isHoveringOverflowChevron(cursor, bounds)) {
    return false
  }
  return true
}

function buildTrayPopupWindowOptions(): Electron.BrowserWindowConstructorOptions {
  const base: Electron.BrowserWindowConstructorOptions = {
    width: TRAY_POPUP_WIDTH,
    height: TRAY_POPUP_HEIGHT,
    show: false,
    frame: false,
    resizable: false,
    movable: false,
    minimizable: false,
    maximizable: false,
    skipTaskbar: true,
    alwaysOnTop: true,
    focusable: true,
    thickFrame: false,
    webPreferences: trayNotifyDeps!.defaultWebPreferences()
  }

  if (process.platform === 'win32') {
    return {
      ...base,
      transparent: true,
      backgroundColor: '#00000000',
      hasShadow: false,
      roundedCorners: false
    }
  }

  return {
    ...base,
    transparent: true,
    backgroundColor: '#00000000',
    hasShadow: false,
    roundedCorners: false
  }
}

function applyTrayPopupNativeChrome(win: BrowserWindow): void {
  if (process.platform !== 'win32' || win.isDestroyed()) return
  try {
    win.setBackgroundColor('#00000000')
  } catch {
    /* ignore */
  }
}

function prefetchTrayMessageWindow(): void {
  if (!trayNotifyDeps) return
  if (trayMessageWindow && !trayMessageWindow.isDestroyed()) {
    if (trayPopupWindowGeneration === TRAY_POPUP_WINDOW_GENERATION) return
    trayMessageWindow.destroy()
    trayMessageWindow = null
    trayMessageReady = false
  }

  trayMessageWindow = new BrowserWindow(buildTrayPopupWindowOptions())
  trayPopupWindowGeneration = TRAY_POPUP_WINDOW_GENERATION
  trayMessageReady = false

  trayMessageWindow.on('closed', () => {
    trayMessageWindow = null
    trayMessageReady = false
  })

  trayMessageWindow.webContents.once('did-finish-load', () => {
    trayMessageReady = true
    if (pendingTrayPayload) {
      trayMessageWindow?.webContents.send('tray-message:data', pendingTrayPayload)
    }
    if ((hoveringTrayIcon || pendingShowTrayPopup) && pendingTrayPayload) {
      showTrayMessagePopup()
    }
  })

  const { isDev, devServerUrl, distHtml } = trayNotifyDeps
  if (isDev && devServerUrl) {
    trayMessageWindow.loadURL(`${devServerUrl}#/tray-message`)
  } else {
    trayMessageWindow.loadFile(distHtml, { hash: 'tray-message' })
  }
}

function showTrayMessagePopup() {
  if (!pendingTrayPayload) return
  if (Date.now() < suppressHoverPopupUntil) return
  prefetchTrayMessageWindow()
  const win = trayMessageWindow
  if (!win || win.isDestroyed()) return
  if (!trayMessageReady) {
    pendingShowTrayPopup = true
    return
  }
  pendingShowTrayPopup = false

  win.webContents.send('tray-message:data', pendingTrayPayload)
  positionTrayMessageNearTray()
  if (!win.isVisible()) {
    applyTrayPopupNativeChrome(win)
    win.showInactive()
  }
  win.setAlwaysOnTop(true, 'pop-up-menu')
}

function positionTrayMessageNearTray() {
  const win = trayMessageWindow
  if (!win || win.isDestroyed()) return

  const winBounds = win.getBounds()
  let anchorX = 0
  let anchorY = 0
  let anchorW = 24
  let anchorH = 24
  let hasAnchor = false

  if (trayRef && !trayRef.isDestroyed()) {
    const trayBounds = trayRef.getBounds()
    if (trayBounds.width > 0 && trayBounds.height > 0) {
      anchorX = trayBounds.x
      anchorY = trayBounds.y
      anchorW = trayBounds.width
      anchorH = trayBounds.height
      hasAnchor = true
    }
  }

  if (!hasAnchor) {
    const cursor = screen.getCursorScreenPoint()
    anchorX = cursor.x - 12
    anchorY = cursor.y - 12
    const display = screen.getDisplayNearestPoint(cursor)
    const { workArea } = display
    // 溢出区托盘通常在工作区右下
    if (cursor.y > workArea.y + workArea.height - 80) {
      anchorY = workArea.y + workArea.height - 24
    }
  }

  let x = Math.round(anchorX + anchorW / 2 - winBounds.width / 2)
  let y = Math.round(anchorY - winBounds.height - 8)

  const display = screen.getDisplayNearestPoint({ x: anchorX, y: anchorY })
  const { workArea } = display
  x = Math.min(Math.max(workArea.x + 8, x), workArea.x + workArea.width - winBounds.width - 8)
  if (y < workArea.y + 8) {
    y = Math.round(anchorY + anchorH + 8)
  }

  win.setPosition(x, y)
}

function isCursorOnTrayOrPopup(): boolean {
  if (hoveringPopup || trayMouseInside || hoveringTrayIcon) return true
  if (shouldShowTrayHoverPreview()) return true
  const cursor = screen.getCursorScreenPoint()
  if (trayMessageWindow && !trayMessageWindow.isDestroyed() && trayMessageWindow.isVisible()) {
    const b = trayMessageWindow.getBounds()
    if (
      cursor.x >= b.x &&
      cursor.x <= b.x + b.width &&
      cursor.y >= b.y &&
      cursor.y <= b.y + b.height
    ) {
      return true
    }
  }
  return false
}

function scheduleHideTrayPopup() {
  if (hideTrayPopupTimer) clearTimeout(hideTrayPopupTimer)
  hideTrayPopupTimer = setTimeout(() => {
    hideTrayPopupTimer = null
    if (isCursorOnTrayOrPopup()) return
    hideTrayMessagePopup()
  }, 80)
}

function cancelHideTrayPopup() {
  if (hideTrayPopupTimer) {
    clearTimeout(hideTrayPopupTimer)
    hideTrayPopupTimer = null
  }
}

export function hideTrayMessagePopup(): void {
  hoveringPopup = false
  if (trayMessageWindow && !trayMessageWindow.isDestroyed() && trayMessageWindow.isVisible()) {
    trayMessageWindow.hide()
  }
}

export function getTrayMessagePayload(): TrayMessagePayload | null {
  return pendingTrayPayload
}

export function openTrayMessageFromPopup(): void {
  if (!trayNotifyDeps) return
  const action = pendingTrayPayload?.action
  clearTrayMessageQueue()
  trayNotifyDeps.onOpen(action)
}

export function ignoreAllTrayMessages(): void {
  clearTrayMessageQueue()
}

export function registerTrayBalloonHandlers(tray: Tray, onBalloonClick: () => void): void {
  tray.removeAllListeners('balloon-click')
  tray.removeAllListeners('balloon-closed')
  tray.on('balloon-click', onBalloonClick)
  tray.on('balloon-closed', () => {
    /* noop */
  })
}

export function setTrayPopupHovering(hovering: boolean): void {
  hoveringPopup = hovering
  if (hovering) {
    cancelHideTrayPopup()
  } else {
    scheduleHideTrayPopup()
  }
}

export function createTrayFlashIcon(base: NativeImage): NativeImage {
  return getEmptyTrayIcon().isEmpty() ? nativeImage.createEmpty() : base
}
