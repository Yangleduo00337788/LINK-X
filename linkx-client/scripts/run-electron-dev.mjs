/**
 * 作者：yangleduo
 */
/**
 * Windows 下先切 UTF-8 代码页，再启动 Vite Electron 开发态，避免控制台中文乱码。
 */
import { spawn } from 'node:child_process'
import { execSync } from 'node:child_process'
import { electronMirrorEnv, ensureElectronBinary } from './ensure-electron-binary.mjs'

if (process.platform === 'win32') {
  try {
    execSync('chcp 65001', { stdio: 'ignore' })
  } catch {
    /* ignore */
  }
}

if (!ensureElectronBinary()) {
  process.exit(1)
}

const child = spawn('npx', ['vite', '--mode', 'electron'], {
  stdio: 'inherit',
  shell: true,
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
