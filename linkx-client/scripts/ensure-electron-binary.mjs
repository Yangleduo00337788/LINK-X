/**
 * 作者：yangleduo
 */
/**
 * Electron 42+ 不再在 postinstall 下载二进制，首次 require('electron') 时才拉取。
 * 开发/打包前显式安装，并注入国内镜像，避免 electron:dev 报 fetch failed。
 */
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const electronDir = path.join(rootDir, 'node_modules/electron')
const installScript = path.join(electronDir, 'install.js')
const pathFile = path.join(electronDir, 'path.txt')

export const electronMirrorEnv = {
  ELECTRON_MIRROR: process.env.ELECTRON_MIRROR || 'https://npmmirror.com/mirrors/electron/'
}

function expectedPlatformPath() {
  switch (process.platform) {
    case 'darwin':
      return 'Electron.app/Contents/MacOS/Electron'
    case 'win32':
      return 'electron.exe'
    case 'linux':
    case 'freebsd':
    case 'openbsd':
      return 'electron'
    default:
      return null
  }
}

function isValidPlatformBinary(exePath) {
  if (!fs.existsSync(exePath)) return false
  const fd = fs.openSync(exePath, 'r')
  try {
    const header = Buffer.alloc(4)
    fs.readSync(fd, header, 0, 4, 0)
    if (process.platform === 'win32') {
      return header[0] === 0x4d && header[1] === 0x5a // MZ
    }
    if (process.platform === 'darwin') {
      return header.toString('ascii', 0, 4) === '\xcf\xfa\xed\xfe' || header.toString('ascii', 0, 4) === '\xfe\xed\xfa\xcf'
    }
    if (process.platform === 'linux') {
      return header.toString('ascii', 0, 4) === '\x7fELF'
    }
    return true
  } finally {
    fs.closeSync(fd)
  }
}

function resetElectronDist() {
  const distRoot = process.env.ELECTRON_OVERRIDE_DIST_PATH || path.join(electronDir, 'dist')
  const stampPath = path.join(electronDir, '.linkx-dev-exe')
  if (fs.existsSync(distRoot)) fs.rmSync(distRoot, { recursive: true, force: true })
  if (fs.existsSync(pathFile)) fs.rmSync(pathFile, { force: true })
  if (fs.existsSync(stampPath)) fs.rmSync(stampPath, { force: true })
}

function isElectronBinaryReady() {
  if (!fs.existsSync(pathFile) || !fs.existsSync(installScript)) return false
  const rel = fs.readFileSync(pathFile, 'utf8').trim()
  if (!rel) return false
  const expected = expectedPlatformPath()
  if (expected && rel !== expected) {
    console.warn(
      `[ensure-electron-binary] 检测到错误平台的 Electron（path.txt=${rel}，当前平台应为 ${expected}），将重新下载`
    )
    resetElectronDist()
    return false
  }
  const distRoot = process.env.ELECTRON_OVERRIDE_DIST_PATH || path.join(electronDir, 'dist')
  const exePath = path.join(distRoot, rel)
  if (!isValidPlatformBinary(exePath)) {
    console.warn('[ensure-electron-binary] Electron 二进制与当前系统不匹配，将重新下载')
    resetElectronDist()
    return false
  }
  return true
}

function resolveElectronExePath() {
  if (!fs.existsSync(pathFile)) return null
  const rel = fs.readFileSync(pathFile, 'utf8').trim()
  if (!rel) return null
  const distRoot = process.env.ELECTRON_OVERRIDE_DIST_PATH || path.join(electronDir, 'dist')
  const exePath = path.join(distRoot, rel)
  return fs.existsSync(exePath) ? exePath : null
}

/** 开发态使用独立 LinkX-dev.exe，避免 Windows 缓存 electron.exe 的 Electron 任务栏身份 */
const DEV_EXE_PATCH_VERSION = 3
const DEV_EXE_NAME = 'LinkX-dev.exe'

function resolveElectronDistDir() {
  if (!fs.existsSync(pathFile)) return null
  const rel = fs.readFileSync(pathFile, 'utf8').trim()
  if (!rel) return null
  const distRoot = process.env.ELECTRON_OVERRIDE_DIST_PATH || path.join(electronDir, 'dist')
  const stockExe = path.join(distRoot, rel)
  return fs.existsSync(stockExe) ? distRoot : null
}

function readProductVersion() {
  try {
    const pkg = JSON.parse(fs.readFileSync(path.join(rootDir, 'package.json'), 'utf8'))
    const ver = String(pkg.version || '1.0.0')
    return /^\d+\.\d+\.\d+$/.test(ver) ? `${ver}.0` : ver
  } catch {
    return '1.0.0.0'
  }
}

async function patchElectronDevExe() {
  if (process.platform !== 'win32') return

  const distDir = resolveElectronDistDir()
  const iconPath = path.join(rootDir, 'build', 'icon.ico')
  const stampPath = path.join(electronDir, '.linkx-dev-exe')

  if (!distDir || !fs.existsSync(iconPath)) return

  const stockExe = path.join(distDir, fs.readFileSync(pathFile, 'utf8').trim())
  if (!fs.existsSync(stockExe)) return

  const brandedExe = path.join(distDir, DEV_EXE_NAME)
  const iconStat = fs.statSync(iconPath)
  const stockStat = fs.statSync(stockExe)
  const productVersion = readProductVersion()
  const stamp = `${DEV_EXE_PATCH_VERSION}:${productVersion}:${stockStat.mtimeMs}:${stockStat.size}:${iconStat.mtimeMs}:${iconStat.size}`
  if (fs.existsSync(stampPath) && fs.readFileSync(stampPath, 'utf8') === stamp && fs.existsSync(brandedExe)) {
    return
  }

  console.log('[ensure-electron-binary] 准备开发态任务栏品牌 (LinkX-dev.exe)')
  fs.copyFileSync(stockExe, brandedExe)

  const { rcedit } = await import('rcedit')
  await rcedit(brandedExe, {
    icon: iconPath,
    'file-version': productVersion,
    'product-version': productVersion,
    'version-string': {
      FileDescription: 'LinkX',
      ProductName: 'LinkX',
      InternalName: 'LinkX',
      OriginalFilename: 'LinkX.exe',
      CompanyName: 'LinkX',
      LegalCopyright: 'Copyright (C) LinkX'
    }
  })
  fs.writeFileSync(stampPath, stamp, 'utf8')
  console.log('[ensure-electron-binary] 已写入 LinkX-dev.exe')
}

export function resolveLinkxDevElectronPath() {
  if (process.platform !== 'win32') return resolveElectronExePath()
  const distDir = resolveElectronDistDir()
  if (!distDir) return null
  const brandedExe = path.join(distDir, DEV_EXE_NAME)
  return fs.existsSync(brandedExe) ? brandedExe : resolveElectronExePath()
}

export async function ensureElectronBinary() {
  if (isElectronBinaryReady()) {
    await patchElectronDevExe()
    return true
  }

  if (!fs.existsSync(installScript)) {
    console.error('[ensure-electron-binary] 未找到 electron 包，请先执行 npm install')
    return false
  }

  console.log('[ensure-electron-binary] 正在下载 Electron 运行时（镜像:', electronMirrorEnv.ELECTRON_MIRROR, '）')
  const result = spawnSync(process.execPath, [installScript], {
    cwd: rootDir,
    env: { ...process.env, ...electronMirrorEnv },
    stdio: 'inherit'
  })
  if (result.status !== 0) {
    console.error(
      '[ensure-electron-binary] 下载失败。可手动执行：\n' +
        '  $env:ELECTRON_MIRROR="https://npmmirror.com/mirrors/electron/"; npx install-electron'
    )
    return false
  }
  const ready = isElectronBinaryReady()
  if (ready) await patchElectronDevExe()
  return ready
}

const isCli = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)
if (isCli) {
  ensureElectronBinary()
    .then(ok => process.exit(ok ? 0 : 1))
    .catch(err => {
      console.error('[ensure-electron-binary]', err)
      process.exit(1)
    })
}
