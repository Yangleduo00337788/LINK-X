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

function isElectronBinaryReady() {
  if (!fs.existsSync(pathFile) || !fs.existsSync(installScript)) return false
  const rel = fs.readFileSync(pathFile, 'utf8').trim()
  if (!rel) return false
  const distRoot = process.env.ELECTRON_OVERRIDE_DIST_PATH || path.join(electronDir, 'dist')
  return fs.existsSync(path.join(distRoot, rel))
}

export function ensureElectronBinary() {
  if (isElectronBinaryReady()) return true

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
  return isElectronBinaryReady()
}

const isCli = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)
if (isCli) {
  process.exit(ensureElectronBinary() ? 0 : 1)
}
