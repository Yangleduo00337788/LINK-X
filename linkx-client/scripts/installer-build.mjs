/**
 * 作者：yangleduo
 */
/**
 * 打包自定义图形安装程序（需先由 electron-build 生成 .installer-payload）。
 */
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const rootDir = path.join(__dirname, '..')
const releaseDir = process.env.LINKX_RELEASE_DIR || 'release'
const stagingDir = path.join(rootDir, '.installer-payload')

const env = {
  ...process.env,
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

if (!fs.existsSync(path.join(stagingDir, 'LinkX.exe'))) {
  console.error(`[installer:build] 未找到安装包内容: ${stagingDir}`)
  console.error('请先执行 npm run electron:build')
  process.exit(1)
}

run('npx', ['vite', 'build', '--config', 'vite.installer.config.ts', '--mode', 'production'])

// 勿写入 dist-installer-electron/package.json：会覆盖根 package.json 的 type:module，
// 导致 main.js 被当作 CommonJS 加载而报 "Cannot use import statement outside a module"。
const nestedPkg = path.join(rootDir, 'dist-installer-electron', 'package.json')
if (fs.existsSync(nestedPkg)) {
  fs.unlinkSync(nestedPkg)
}

const pkgPath = path.join(rootDir, 'package.json')
const pkgBackup = fs.readFileSync(pkgPath, 'utf8')
try {
  run('npx', [
    'electron-builder',
    '--projectDir',
    rootDir,
    '--config',
    'electron-builder.installer.yml'
  ])
} finally {
  fs.writeFileSync(pkgPath, pkgBackup, 'utf8')
}

console.log(`[installer:build] 完成，输出见 ${releaseDir}/installer/LinkX-Installer-*.exe`)
