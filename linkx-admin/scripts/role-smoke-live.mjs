#!/usr/bin/env node
/**
 * 生产五角色 API 抽检入口 → vitest live 用例
 * @see docs/testing/ADMIN_FIVE_ROLE_CHECKLIST.md
 */
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')

function hasCredentials() {
  if (process.env.ROLE_SMOKE_CREDENTIALS) return true
  const keys = [
    'ADMIN_SMOKE_SUPER_USER',
    'ADMIN_SMOKE_OPS_USER',
    'ADMIN_SMOKE_AUDIT_USER',
    'ADMIN_SMOKE_SECURITY_USER',
    'ADMIN_SMOKE_READONLY_USER',
  ]
  return keys.some((k) => process.env[k])
}

if (!hasCredentials()) {
  console.log(
    '[role-smoke-live] skipped — set ROLE_SMOKE_CREDENTIALS or ADMIN_SMOKE_* env vars (see docs/testing/ADMIN_FIVE_ROLE_CHECKLIST.md)'
  )
  process.exit(0)
}

const r = spawnSync(
  process.platform === 'win32' ? 'npx.cmd' : 'npx',
  ['vitest', 'run', 'src/acceptance/roleSmokeLive.spec.ts'],
  { cwd: root, env: process.env, stdio: 'inherit', shell: process.platform === 'win32' }
)

process.exit(r.status ?? 1)
