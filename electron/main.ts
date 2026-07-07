import { app, BrowserWindow, ipcMain, type IpcMainEvent, type IpcMainInvokeEvent } from 'electron'
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
}

registerWindowIpc()

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
    transparent: true,
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
    // 加载同一个开发服务器地址，但我们可以通过 hash 路由或者给个查询参数区分页面
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
    transparent: true,
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
    width: 966,
    height: 676,
    minWidth: 966,
    minHeight: 676,
    frame: false,
    titleBarStyle: 'hidden',
    transparent: true,
    show: false,
    webPreferences: {
      preload: preloadPath,
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
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

  mainWindow.on('closed', () => {
    mainWindow = null
  })
}

app.whenReady().then(() => {
  registerWindowIpc()
  createWindow()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow()
    }
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})