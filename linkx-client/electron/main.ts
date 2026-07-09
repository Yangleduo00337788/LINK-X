import { app, BrowserWindow, ipcMain, Tray, Menu, nativeImage, globalShortcut, type IpcMainEvent, type IpcMainInvokeEvent } from 'electron'
import path from 'node:path'
import fs from 'node:fs'
import { fileURLToPath } from 'node:url'
import os from 'node:os'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const isDev = Boolean(process.env.VITE_DEV_SERVER_URL)

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

app.commandLine.appendSwitch('--no-sandbox')
app.commandLine.appendSwitch('--disable-software-rasterizer')
app.setPath('userData', path.join(os.tmpdir(), 'linkx-electron'))

let mainWindow: BrowserWindow | null = null
let tray: Tray | null = null

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
      win.setMinimumSize(380, 520)
      win.setMaximumSize(380, 520)
      win.setSize(380, 520, false)
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
      sandbox: false
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
      sandbox: false
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

function createWindow() {
  if (isDev) {
    console.log('[electron] preload:', preloadPath, 'exists:', fs.existsSync(preloadPath))
  }

  mainWindow = new BrowserWindow({
    width: 380,
    height: 520,
    minWidth: 380,
    minHeight: 520,
    maxWidth: 380,
    maxHeight: 520,
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
      sandbox: false,
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
