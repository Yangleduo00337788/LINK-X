/**
 * 作者：yangleduo
 */
/**
 * macOS / Linux 桌面端打包（标准 electron-builder 产物）。
 * Windows 请使用 npm run electron:build（自定义图形安装程序）。
 *
 * 用法：
 *   node ./scripts/electron-build-desktop.mjs mac
 *   node ./scripts/electron-build-desktop.mjs linux
 */
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const rootDir = path.join(__dirname, '..')
const releaseDir = process.env.LINKX_RELEASE_DIR || 'release'

const platform = (process.argv[2] || '').trim().toLowerCase()
if (platform !== 'mac' && platform !== 'linux') {
  console.error('[electron:build] 请指定平台: mac | linux')
  console.error('  Windows: npm run electron:build')
  process.exit(1)
}

const env = {
  ...process.env,
  LINKX_RELEASE_DIR: releaseDir,
  ELECTRON_MIRROR: process.env.ELECTRON_MIRROR || 'https://npmmirror.com/mirrors/electron/',
  ELECTRON_BUILDER_BINARIES_MIRROR:
    process.env.ELECTRON_BUILDER_BINARIES_MIRROR ||
    'https://npmmirror.com/mirrors/electron-builder-binaries/',
  CSC_IDENTITY_AUTO_DISCOVERY: process.env.CSC_IDENTITY_AUTO_DISCOVERY || 'false'
}

function run(command, args) {
  const result = spawnSync(command, args, {
    cwd: rootDir,
    env,
    stdio: 'inherit',
    shell: process.platform === 'win32'
  })
  if (result.status !== 0) {
    process.exit(result.status ?? 1)
  }
}

run('node', ['./scripts/generate-installer-assets.mjs'])
run('npx', ['vue-tsc', '--noEmit'])
run('npx', ['vite', 'build', '--mode', 'electron'])
run('npx', [
  'electron-builder',
  `--config.directories.output=${releaseDir}`,
  platform === 'mac' ? '--mac' : '--linux'
])

const artifactHint =
  platform === 'mac'
    ? `${releaseDir}/LinkX-*.dmg`
    : `${releaseDir}/LinkX-*.AppImage 与 ${releaseDir}/linkx_*_amd64.deb`

console.log(`[electron:build] 完成，产物: ${artifactHint}`)
