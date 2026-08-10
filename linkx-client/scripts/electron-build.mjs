/**
 * 作者：yangleduo
 */
/**
 * 完整打包自定义安装程序 LinkX-Installer：
 * 1. 编译主应用 → win-unpacked（仅目录，不生成 NSIS）
 * 2. 快照到 .installer-payload
 * 3. 打包自定义图形安装程序
 * 4. 清理中间产物，仅保留 release/installer/LinkX-Installer-*.exe
 */
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const rootDir = path.join(__dirname, '..')
const releaseDir = process.env.LINKX_RELEASE_DIR || 'release'

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

function pruneIntermediateArtifacts() {
  const releaseRoot = path.join(rootDir, releaseDir)
  if (!fs.existsSync(releaseRoot)) return

  const removableDirs = [
    path.join(releaseRoot, 'win-unpacked'),
    path.join(releaseRoot, 'installer', 'win-unpacked')
  ]
  for (const dir of removableDirs) {
    if (fs.existsSync(dir)) {
      fs.rmSync(dir, { recursive: true, force: true })
      console.log(`[electron:build] 已清理中间目录: ${path.relative(rootDir, dir)}`)
    }
  }

  for (const entry of fs.readdirSync(releaseRoot, { withFileTypes: true })) {
    const fullPath = path.join(releaseRoot, entry.name)
    if (entry.isFile() && /\.(exe|blockmap|yml|yaml)$/i.test(entry.name)) {
      fs.rmSync(fullPath, { force: true })
      console.log(`[electron:build] 已清理: ${path.relative(rootDir, fullPath)}`)
    }
  }

  const installerDir = path.join(releaseRoot, 'installer')
  if (fs.existsSync(installerDir)) {
    for (const entry of fs.readdirSync(installerDir, { withFileTypes: true })) {
      if (!entry.isFile()) continue
      if (!entry.name.startsWith('LinkX-Installer-') || !entry.name.endsWith('.exe')) {
        fs.rmSync(path.join(installerDir, entry.name), { force: true })
        console.log(`[electron:build] 已清理: ${path.relative(rootDir, path.join(installerDir, entry.name))}`)
      }
    }
  }

  fs.rmSync(path.join(rootDir, '.installer-payload'), { recursive: true, force: true })
  console.log('[electron:build] 已清理: .installer-payload')
}

run('node', ['./scripts/generate-installer-assets.mjs'])
run('npx', ['vue-tsc', '--noEmit'])
run('npx', ['vite', 'build', '--mode', 'electron'])
run('npx', ['electron-builder', `--config.directories.output=${releaseDir}`, '--win', 'dir'])

const payloadSrc = path.join(rootDir, releaseDir, 'win-unpacked')
const stagingDir = path.join(rootDir, '.installer-payload')
if (!fs.existsSync(path.join(payloadSrc, 'LinkX.exe'))) {
  console.error(`[electron:build] 未找到 LinkX 可执行文件: ${payloadSrc}`)
  process.exit(1)
}
fs.rmSync(stagingDir, { recursive: true, force: true })
fs.cpSync(payloadSrc, stagingDir, { recursive: true })

run('node', ['./scripts/installer-build.mjs'])
pruneIntermediateArtifacts()

console.log(`[electron:build] 完成，安装包: ${releaseDir}/installer/LinkX-Installer-*.exe`)
