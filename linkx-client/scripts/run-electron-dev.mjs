/**
 * 作者：yangleduo
 */
/**
 * Windows 下先切 UTF-8 代码页，再启动 Vite Electron 开发态，避免控制台中文乱码。
 */
import { spawn } from 'node:child_process'
import { execSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { electronMirrorEnv, ensureElectronBinary } from './ensure-electron-binary.mjs'

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const viteBin = path.join(rootDir, 'node_modules/vite/bin/vite.js')

if (process.platform === 'win32') {
  try {
    execSync('chcp 65001', { stdio: 'ignore' })
  } catch {
    /* ignore */
  }
}

const ready = await ensureElectronBinary()
if (!ready) {
  process.exit(1)
}

if (!fs.existsSync(viteBin)) {
  console.error('[electron:dev] 未找到 vite，请在 linkx-client 目录执行: npm install')
  process.exit(1)
}

const child = spawn(process.execPath, [viteBin, '--mode', 'electron'], {
  cwd: rootDir,
  stdio: 'inherit',
  env: {
    ...process.env,
    ...electronMirrorEnv,
    // 提示 Node 使用 UTF-8（部分终端会读取）
    PYTHONIOENCODING: 'utf-8'
  }
})

child.on('exit', code => {
  process.exit(code ?? 0)
})
