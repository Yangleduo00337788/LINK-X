/**
 * 作者：yangleduo
 */
/**
 * 启动自定义安装向导的开发模式（需已存在 release/win-unpacked）。
 */
import { spawn } from 'node:child_process'
import { execSync } from 'node:child_process'

if (process.platform === 'win32') {
  try {
    execSync('chcp 65001', { stdio: 'ignore' })
  } catch {
    /* ignore */
  }
}

const child = spawn('npx', ['vite', '--config', 'vite.installer.config.ts'], {
  stdio: 'inherit',
  shell: true,
  env: {
    ...process.env,
    PYTHONIOENCODING: 'utf-8'
  }
})

child.on('exit', code => {
  process.exit(code ?? 0)
})
