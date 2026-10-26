/**
 * 作者：yangleduo
 */
import { spawn } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const demoRoot = fileURLToPath(new URL('..', import.meta.url))

const children = [
  ['vite', ['--config', 'vite.portal.config.ts'], 'portal :5200'],
  ['vite', ['--config', 'vite.admin.config.ts'], 'admin :5201'],
  ['vite', ['--config', 'vite.client.config.ts'], 'client :5202'],
].map(([cmd, args, label]) => {
  const child = spawn(cmd, args, {
    cwd: demoRoot,
    stdio: 'inherit',
    shell: true,
  })
  child.on('exit', code => {
    if (code && code !== 0) process.exit(code)
  })
  console.log(`[linkx-demo] started ${label}`)
  return child
})

function shutdown() {
  for (const c of children) c.kill('SIGTERM')
  process.exit(0)
}

process.on('SIGINT', shutdown)
process.on('SIGTERM', shutdown)
