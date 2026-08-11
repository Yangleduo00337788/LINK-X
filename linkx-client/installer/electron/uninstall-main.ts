/**
 * 作者：yangleduo
 */
import { app, BrowserWindow, ipcMain } from 'electron'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  APP_NAME,
  resolveUninstallTargetDir,
  runUninstall
} from './uninstallActions'
import { installerT, resolveInstallerLocale, type InstallerLocale } from '../shared/i18n'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const APP_VERSION = process.env.LINKX_VERSION || '1.0.0'

let mainWindow: BrowserWindow | null = null
let uninstallerLocale: InstallerLocale = 'zh-CN'

function t(key: string, params?: Record<string, string | number>): string {
  return installerT(uninstallerLocale, key, params)
}

function getPreloadPath(): string {
  return path.join(__dirname, '../preload/preload.cjs')
}

function getRendererUrl(): string {
  if (app.isPackaged) {
    return path.join(__dirname, '../../dist-installer/uninstall.html')
  }
  return (process.env.VITE_DEV_SERVER_URL || 'http://localhost:5174') + '/uninstall.html'
}

function getAppIconPath(): string {
  const candidates = app.isPackaged
    ? [path.join(process.resourcesPath, 'icon.ico')]
    : [
        path.resolve(__dirname, '../../../build/icon.ico'),
        path.resolve(__dirname, '../../../build/icon.png')
      ]
  return candidates.find(candidate => fs.existsSync(candidate)) || candidates[0]
}

function windowBackgroundColor(): string {
  return 'rgba(255, 255, 255, 0)'
}

function prepareFramelessWindow(win: BrowserWindow): void {
  const refreshBg = () => {
    if (win.isDestroyed()) return
    win.setBackgroundColor(windowBackgroundColor())
  }
  refreshBg()
  win.on('focus', refreshBg)
  win.on('blur', refreshBg)
}

function createWindow(): BrowserWindow {
  const win = new BrowserWindow({
    width: 639,
    height: 477,
    minWidth: 639,
    minHeight: 477,
    resizable: false,
    maximizable: false,
    frame: false,
    transparent: true,
    backgroundColor: windowBackgroundColor(),
    roundedCorners: false,
    hasShadow: false,
    show: false,
    icon: getAppIconPath(),
    webPreferences: {
      preload: getPreloadPath(),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  })

  prepareFramelessWindow(win)

  const url = getRendererUrl()
  if (url.startsWith('http')) {
    void win.loadURL(url)
  } else {
    void win.loadFile(url)
  }

  win.once('ready-to-show', () => win.show())
  return win
}

function sendProgress(percent: number, status: string): void {
  mainWindow?.webContents.send('uninstaller:progress', { percent, status })
}

function registerIpcHandlers(installDir: string): void {
  ipcMain.handle('uninstaller:get-defaults', () => ({
    version: APP_VERSION,
    installDir,
    appName: APP_NAME,
    locale: uninstallerLocale
  }))

  ipcMain.handle(
    'uninstaller:start',
    async (_event, options: { removeUserData?: boolean }) => {
      try {
        await runUninstall({
          installDir,
          removeUserData: options?.removeUserData !== false,
          onProgress: sendProgress,
          locale: uninstallerLocale
        })
        return { ok: true as const }
      } catch (error) {
        return {
          ok: false as const,
          message: error instanceof Error ? error.message : t('uninstallFail')
        }
      }
    }
  )

  ipcMain.on('uninstaller:close', () => {
    app.quit()
  })

  ipcMain.on('uninstaller:minimize', () => {
    mainWindow?.minimize()
  })

  ipcMain.on('uninstaller:set-window-size', (_event, size: { width?: number; height?: number }) => {
    if (!mainWindow || mainWindow.isDestroyed()) return
    const width = Math.max(480, Math.round(size?.width || 639))
    const height = Math.max(360, Math.round(size?.height || 477))
    const bounds = mainWindow.getBounds()
    mainWindow.setBounds({
      x: bounds.x,
      y: bounds.y,
      width,
      height
    })
  })
}

async function runSilentUninstall(argv: string[]): Promise<void> {
  const installDir = resolveUninstallTargetDir(argv)
  await runUninstall({
    installDir,
    removeUserData: true,
    locale: uninstallerLocale
  })
  app.quit()
}

async function bootstrap(): Promise<void> {
  if (process.platform !== 'win32') {
    console.error(t('windowsOnlyUninstall'))
    app.quit()
    return
  }

  const argv = process.argv.slice(1)
  const silent = argv.some(arg => arg === '/S' || arg === '--silent')
  const installDir = resolveUninstallTargetDir(argv)

  await app.whenReady()
  uninstallerLocale = resolveInstallerLocale(app.getLocale())
  registerIpcHandlers(installDir)

  if (silent) {
    try {
      await runSilentUninstall(argv)
    } catch (error) {
      console.error('[uninstaller] 静默卸载失败:', error)
      app.exit(1)
    }
    return
  }

  mainWindow = createWindow()
}

app.on('window-all-closed', () => {
  app.quit()
})

bootstrap().catch(error => {
  console.error('[uninstaller] 启动失败:', error)
  app.exit(1)
})
