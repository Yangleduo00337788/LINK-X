/**
 * 作者：yangleduo
 */
import { app } from 'electron'
import fs from 'node:fs'
import path from 'node:path'
import { execSync, spawn } from 'node:child_process'
import { installerT, type InstallerLocale } from '../shared/i18n'

export const APP_NAME = 'LinkX'
export const APP_EXE = 'LinkX.exe'

export type UninstallProgress = (percent: number, status: string) => void

function safeUnlink(filePath: string): void {
  try {
    if (fs.existsSync(filePath)) {
      fs.unlinkSync(filePath)
    }
  } catch {
    /* ignore */
  }
}

function safeRmDir(dirPath: string): void {
  try {
    if (fs.existsSync(dirPath)) {
      fs.rmSync(dirPath, { recursive: true, force: true })
    }
  } catch {
    /* ignore */
  }
}

export async function killRunningApp(): Promise<void> {
  try {
    execSync(`taskkill /F /IM ${APP_EXE}`, { stdio: 'pipe' })
  } catch {
    /* 未在运行 */
  }
  await new Promise(resolve => setTimeout(resolve, 800))
}

export function removeShortcuts(): void {
  const targets = [
    path.join(app.getPath('desktop'), `${APP_NAME}.lnk`),
    path.join(
      app.getPath('appData'),
      'Microsoft',
      'Windows',
      'Start Menu',
      'Programs',
      `${APP_NAME}.lnk`
    )
  ]
  for (const shortcut of targets) {
    safeUnlink(shortcut)
  }
}

export function removeAutoStart(): void {
  try {
    execSync(`reg delete "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run" /v "${APP_NAME}" /f`, {
      stdio: 'pipe'
    })
  } catch {
    /* 未配置过启动项 */
  }
}

export function removeUninstallRegistry(): void {
  try {
    execSync('reg delete "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\LinkX" /f', {
      stdio: 'pipe'
    })
  } catch {
    /* ignore */
  }
}

export function removeUserDataDir(): void {
  safeRmDir(path.join(app.getPath('appData'), 'linkx'))
}

/** 卸载程序自身在安装目录内，需延迟删除整个安装文件夹 */
export function scheduleInstallDirRemoval(installDir: string): void {
  const escaped = installDir.replace(/"/g, '""')
  const batPath = path.join(app.getPath('temp'), `linkx-uninstall-${Date.now()}.bat`)
  const content = `@echo off\r\nping 127.0.0.1 -n 3 >nul\r\nrd /s /q "${escaped}"\r\ndel /f /q "%~f0"\r\n`
  fs.writeFileSync(batPath, content, 'utf8')
  spawn('cmd.exe', ['/c', batPath], {
    detached: true,
    stdio: 'ignore',
    windowsHide: true
  }).unref()
}

export function validateInstallDir(installDir: string, locale: InstallerLocale = 'zh-CN'): void {
  const exePath = path.join(installDir, APP_EXE)
  if (!fs.existsSync(exePath)) {
    throw new Error(installerT(locale, 'installDirNotFound'))
  }
}

export async function runUninstall(options: {
  installDir: string
  removeUserData: boolean
  onProgress?: UninstallProgress
  locale?: InstallerLocale
}): Promise<void> {
  const { installDir, removeUserData, onProgress } = options
  const locale = options.locale ?? 'zh-CN'
  const t = (key: string, params?: Record<string, string | number>) =>
    installerT(locale, key, params)

  validateInstallDir(installDir, locale)

  onProgress?.(5, t('closingApp'))
  await killRunningApp()

  onProgress?.(25, t('removingShortcuts'))
  removeShortcuts()

  onProgress?.(45, t('cleaningAutostart'))
  removeAutoStart()

  onProgress?.(60, t('removingRegistry'))
  removeUninstallRegistry()

  if (removeUserData) {
    onProgress?.(78, t('cleaningUserData'))
    removeUserDataDir()
  }

  onProgress?.(92, t('deletingFiles'))
  scheduleInstallDirRemoval(installDir)

  onProgress?.(100, t('uninstallComplete'))
}

export function resolveInstallDirFromArgv(argv: string[]): string {
  const shortDir = argv.find(arg => arg.startsWith('/D='))
  if (shortDir) {
    return shortDir.slice(3).replace(/^"|"$/g, '')
  }
  const longDir = argv.find(arg => arg.startsWith('--install-dir='))
  if (longDir) {
    return longDir.slice('--install-dir='.length).replace(/^"|"$/g, '')
  }
  return ''
}

export function defaultInstallDir(): string {
  const localAppData = process.env.LOCALAPPDATA || path.join(app.getPath('home'), 'AppData', 'Local')
  return path.join(localAppData, 'Programs', APP_NAME)
}

function readInstallDirFromRegistry(): string {
  try {
    const output = execSync(
      'reg query "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\LinkX" /v InstallLocation',
      { encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] }
    )
    const match = output.match(/InstallLocation\s+REG_\w+\s+(.+)/i)
    return match?.[1]?.trim() || ''
  } catch {
    return ''
  }
}

function isValidInstallDir(installDir: string): boolean {
  if (!installDir) return false
  return fs.existsSync(path.join(installDir, APP_EXE))
}

export function resolveUninstallTargetDir(argv: string[]): string {
  const fromArg = resolveInstallDirFromArgv(argv)
  if (fromArg) return fromArg

  const fromRegistry = readInstallDirFromRegistry()
  if (isValidInstallDir(fromRegistry)) return fromRegistry

  // portable 卸载程序会解压到临时目录，process.execPath 并非安装目录
  const besideUninstaller = path.dirname(process.execPath)
  if (isValidInstallDir(besideUninstaller)) return besideUninstaller

  return defaultInstallDir()
}
