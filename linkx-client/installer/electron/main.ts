/**
 * 作者：yangleduo
 */
import { app, BrowserWindow, dialog, ipcMain, shell } from 'electron'
import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'
import { createRequire } from 'node:module'
import { execSync, spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { buildLegalPageUrl, resolveLegalPageBaseUrl, type LegalDocKind } from '../../shared/legalPage'

const nodeRequire = createRequire(import.meta.url)
// Electron 会拦截路径中含 app.asar 的 fs 操作；安装复制需使用未打补丁的 fs。
const rawFs = nodeRequire('original-fs') as typeof fs

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const APP_NAME = 'LinkX'
const APP_EXE = 'LinkX.exe'
const APP_VERSION = process.env.LINKX_VERSION || '1.0.0'

let mainWindow: BrowserWindow | null = null
let installDir = ''
let appExePath = ''

interface InstallArgs {
  silent: boolean
  installDir: string
}

function parseArgs(): InstallArgs {
  const argv = process.argv.slice(1)
  const silent = argv.some(arg => arg === '/S' || arg === '--silent')
  let dir = ''

  const shortDir = argv.find(arg => arg.startsWith('/D='))
  if (shortDir) {
    dir = shortDir.slice(3).replace(/^"|"$/g, '')
  }

  const longDir = argv.find(arg => arg.startsWith('--install-dir='))
  if (longDir) {
    dir = longDir.slice('--install-dir='.length).replace(/^"|"$/g, '')
  }

  return { silent, installDir: dir }
}

function defaultInstallDir(): string {
  const localAppData = process.env.LOCALAPPDATA || path.join(os.homedir(), 'AppData', 'Local')
  return path.join(localAppData, 'Programs', APP_NAME)
}

function getPayloadDir(): string {
  if (app.isPackaged) {
    const candidates = [
      path.join(process.resourcesPath, 'app-payload'),
      path.join(path.dirname(process.execPath), 'resources', 'app-payload')
    ]
    for (const dir of candidates) {
      if (validatePayloadDir(dir)) return dir
    }
    return candidates[0]
  }

  const devPayload = path.resolve(__dirname, '../../../release/win-unpacked')
  if (validatePayloadDir(devPayload)) {
    return devPayload
  }

  throw new Error('未找到应用安装包，请先执行 npm run electron:build 生成 win-unpacked')
}

function validatePayloadDir(dir: string): boolean {
  return (
    rawFs.existsSync(path.join(dir, APP_EXE)) &&
    rawFs.existsSync(path.join(dir, 'resources', 'app.asar'))
  )
}

function assertPayloadReady(payloadDir: string): void {
  if (!validatePayloadDir(payloadDir)) {
    throw new Error('安装包内容不完整，请重新下载安装程序或联系技术支持')
  }
}

function getPreloadPath(): string {
  return path.join(__dirname, '../preload/preload.cjs')
}

function getRendererUrl(): string {
  if (app.isPackaged) {
    return path.join(__dirname, '../../dist-installer/index.html')
  }
  return process.env.VITE_DEV_SERVER_URL || 'http://localhost:5174'
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
    win.loadURL(url)
  } else {
    win.loadFile(url)
  }

  win.once('ready-to-show', () => win.show())
  return win
}

async function collectFiles(
  srcDir: string
): Promise<{ src: string; rel: string; size: number }[]> {
  const files: { src: string; rel: string; size: number }[] = []

  async function walk(current: string, rel = ''): Promise<void> {
    const entries = await rawFs.promises.readdir(current, { withFileTypes: true })
    for (const entry of entries) {
      const relPath = rel ? `${rel}/${entry.name}` : entry.name
      const fullPath = path.join(current, entry.name)
      if (entry.isDirectory()) {
        await walk(fullPath, relPath)
      } else {
        const stat = await rawFs.promises.stat(fullPath)
        files.push({ src: fullPath, rel: relPath.replace(/\//g, path.sep), size: stat.size })
      }
    }
  }

  await walk(srcDir)
  return files
}

function sendProgress(percent: number, status: string): void {
  mainWindow?.webContents.send('installer:progress', { percent, status })
}

async function copyPayload(
  srcDir: string,
  destDir: string,
  onProgress?: (percent: number, status: string) => void
): Promise<void> {
  const files = await collectFiles(srcDir)
  const totalBytes = files.reduce((sum, file) => sum + file.size, 0)
  let copiedBytes = 0

  await rawFs.promises.mkdir(destDir, { recursive: true })

  for (const file of files) {
    const destPath = path.join(destDir, file.rel)
    await rawFs.promises.mkdir(path.dirname(destPath), { recursive: true })
    await rawFs.promises.copyFile(file.src, destPath)
    copiedBytes += file.size
    const percent = totalBytes > 0 ? Math.min(100, Math.round((copiedBytes / totalBytes) * 100)) : 100
    onProgress?.(percent, `正在复制：${file.rel}`)
  }
}

function resolveInstalledIconPath(targetDir: string, payloadDir: string): string {
  const candidates = [
    path.join(targetDir, 'icon.ico'),
    path.join(targetDir, 'resources', 'build', 'icon.ico'),
    path.join(payloadDir, 'resources', 'build', 'icon.ico'),
    path.join(targetDir, APP_EXE)
  ]
  return candidates.find(candidate => fs.existsSync(candidate)) || path.join(targetDir, APP_EXE)
}

async function copyInstallIcon(payloadDir: string, targetDir: string): Promise<string> {
  const src = path.join(payloadDir, 'resources', 'build', 'icon.ico')
  const dest = path.join(targetDir, 'icon.ico')
  if (rawFs.existsSync(src)) {
    await rawFs.promises.copyFile(src, dest)
    return dest
  }
  return resolveInstalledIconPath(targetDir, payloadDir)
}

function createShortcut(shortcutPath: string, targetPath: string, iconPath?: string): void {
  const workingDir = path.dirname(targetPath)
  const icon = iconPath || targetPath
  const ok = shell.writeShortcutLink(shortcutPath, {
    target: targetPath,
    cwd: workingDir,
    icon,
    iconIndex: 0,
    description: APP_NAME
  })
  if (!ok) {
    throw new Error(`创建快捷方式失败: ${shortcutPath}`)
  }
}

function registerUninstall(targetDir: string, uninstallerPath: string, iconPath?: string): void {
  const key = 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\LinkX'
  const setValue = (name: string, type: string, value: string) => {
    execSync(`reg add "${key}" /v "${name}" /t ${type} /d "${value.replace(/"/g, '\\"')}" /f`, {
      stdio: 'pipe'
    })
  }

  setValue('DisplayName', 'REG_SZ', APP_NAME)
  setValue('DisplayVersion', 'REG_SZ', APP_VERSION)
  setValue('Publisher', 'REG_SZ', 'LinkX')
  setValue('InstallLocation', 'REG_SZ', targetDir)
  setValue('UninstallString', 'REG_SZ', `"${uninstallerPath}" /D="${targetDir}"`)
  setValue('DisplayIcon', 'REG_SZ', iconPath || path.join(targetDir, APP_EXE))
  setValue('NoModify', 'REG_DWORD', '1')
  setValue('NoRepair', 'REG_DWORD', '1')
}

function getUninstallerSourcePath(): string {
  if (app.isPackaged) {
    const bundledDir = path.join(process.resourcesPath, 'uninstaller')
    if (fs.existsSync(bundledDir)) {
      const match = fs
        .readdirSync(bundledDir)
        .find(name => /^Uninstall LinkX.*\.exe$/i.test(name))
      if (match) {
        return path.join(bundledDir, match)
      }
    }
    throw new Error('未找到卸载程序资源，请重新下载安装包')
  }

  const devCandidates = [
    path.resolve(__dirname, '../../../release/uninstaller/Uninstall LinkX.exe'),
    path.resolve(__dirname, '../../../release/uninstaller/Uninstall LinkX-1.0.0.exe')
  ]
  const found = devCandidates.find(candidate => fs.existsSync(candidate))
  if (found) return found

  throw new Error('未找到卸载程序，请先执行 npm run electron:build 生成卸载程序')
}

async function deployUninstaller(targetDir: string): Promise<string> {
  const src = getUninstallerSourcePath()
  const dest = path.join(targetDir, 'Uninstall LinkX.exe')
  await rawFs.promises.copyFile(src, dest)
  return dest
}

function setAutoStartOnBoot(exePath: string, enabled: boolean): void {
  const key = 'HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run'
  if (enabled) {
    execSync(`reg add "${key}" /v "${APP_NAME}" /t REG_SZ /d "${exePath.replace(/"/g, '\\"')}" /f`, {
      stdio: 'pipe'
    })
    return
  }
  try {
    execSync(`reg delete "${key}" /v "${APP_NAME}" /f`, { stdio: 'pipe' })
  } catch {
    // 未配置过启动项时忽略
  }
}

function launchInstalledApp(): void {
  if (!appExePath || !fs.existsSync(appExePath)) return
  spawn(appExePath, [], { detached: true, stdio: 'ignore' }).unref()
}

const LEGAL_PAGE_BASE_URL = resolveLegalPageBaseUrl(process.env.VITE_LEGAL_PAGE_BASE_URL)

function openLegalPage(kind: LegalDocKind, locale = 'zh-CN'): void {
  const url = buildLegalPageUrl(kind, locale, LEGAL_PAGE_BASE_URL)
  void shell.openExternal(url)
}

async function runInstall(options: {
  targetDir: string
  desktopShortcut: boolean
  startMenuShortcut: boolean
  autoStartOnBoot: boolean
  launchAfter: boolean
  reportProgress?: boolean
}): Promise<void> {
  const payloadDir = getPayloadDir()
  assertPayloadReady(payloadDir)

  installDir = options.targetDir
  appExePath = path.join(installDir, APP_EXE)

  const progress = options.reportProgress
    ? (percent: number, status: string) => sendProgress(percent, status)
    : undefined

  progress?.(0, '准备安装...')
  await copyPayload(payloadDir, installDir, progress)

  progress?.(96, '创建快捷方式...')
  const iconPath = await copyInstallIcon(payloadDir, installDir)
  if (options.desktopShortcut) {
    const desktop = path.join(app.getPath('desktop'), `${APP_NAME}.lnk`)
    createShortcut(desktop, appExePath, iconPath)
  }
  if (options.startMenuShortcut) {
    const startMenu = path.join(
      app.getPath('appData'),
      'Microsoft',
      'Windows',
      'Start Menu',
      'Programs',
      `${APP_NAME}.lnk`
    )
    createShortcut(startMenu, appExePath, iconPath)
  }

  progress?.(98, '写入卸载信息...')
  const uninstallerPath = await deployUninstaller(installDir)
  registerUninstall(installDir, uninstallerPath, iconPath)

  progress?.(99, '配置启动项...')
  setAutoStartOnBoot(appExePath, options.autoStartOnBoot)

  progress?.(100, '安装完成')
  if (options.launchAfter) {
    launchInstalledApp()
  }
}

async function runSilentInstall(args: InstallArgs): Promise<void> {
  const targetDir = args.installDir || defaultInstallDir()
  await runInstall({
    targetDir,
    desktopShortcut: true,
    startMenuShortcut: true,
    autoStartOnBoot: false,
    launchAfter: true,
    reportProgress: false
  })
}

function registerIpcHandlers(): void {
  ipcMain.handle('installer:get-defaults', () => ({
    version: APP_VERSION,
    defaultDir: defaultInstallDir(),
    appExe: APP_EXE
  }))

  ipcMain.handle('installer:browse-directory', async (_event, current: string) => {
    const result = await dialog.showOpenDialog(mainWindow!, {
      title: '选择安装目录',
      defaultPath: current || defaultInstallDir(),
      properties: ['openDirectory', 'createDirectory']
    })
    if (result.canceled || !result.filePaths[0]) return null
    return result.filePaths[0]
  })

  ipcMain.handle(
    'installer:start',
    async (
      _event,
      options: {
        installDir: string
        desktopShortcut: boolean
        startMenuShortcut: boolean
        autoStartOnBoot: boolean
        launchAfter: boolean
      }
    ) => {
      try {
        const targetDir = (options.installDir || defaultInstallDir()).trim()
        if (!targetDir) {
          return { ok: false, message: '请选择安装目录' }
        }

        await runInstall({
          targetDir,
          desktopShortcut: options.desktopShortcut,
          startMenuShortcut: options.startMenuShortcut,
          autoStartOnBoot: options.autoStartOnBoot,
          launchAfter: options.launchAfter,
          reportProgress: true
        })
        return { ok: true }
      } catch (error) {
        return {
          ok: false,
          message: error instanceof Error ? error.message : '安装失败'
        }
      }
    }
  )

  ipcMain.handle('installer:launch-app', async () => {
    launchInstalledApp()
  })

  ipcMain.on('installer:close', () => {
    app.quit()
  })

  ipcMain.on('installer:minimize', () => {
    mainWindow?.minimize()
  })

  ipcMain.on('installer:open-external', (_event, url: string) => {
    if (/^https?:\/\//i.test(url)) {
      shell.openExternal(url)
    }
  })

  ipcMain.on('installer:open-legal', (_event, kind: LegalDocKind) => {
    if (kind === 'service' || kind === 'privacy') {
      openLegalPage(kind)
    }
  })

  ipcMain.on('installer:set-window-size', (_event, size: { width?: number; height?: number }) => {
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

async function bootstrap(): Promise<void> {
  if (process.platform !== 'win32') {
    console.error('LinkX 安装程序仅支持 Windows')
    app.quit()
    return
  }

  await app.whenReady()
  registerIpcHandlers()
  const args = parseArgs()

  if (args.silent) {
    try {
      await runSilentInstall(args)
      app.quit()
    } catch (error) {
      console.error('[installer] 静默安装失败:', error)
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
  console.error('[installer] 启动失败:', error)
  app.exit(1)
})
