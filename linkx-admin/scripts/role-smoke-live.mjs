#!/usr/bin/env node
/**
 * 生产五角色 API 抽检入口 → vitest live 用例
 * @see docs/testing/ADMIN_FIVE_ROLE_CHECKLIST.md
 */
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const env = { ...process.env, ROLE_SMOKE_LIVE: '1' }

const r = spawnSync(
  process.platform === 'win32' ? 'npx.cmd' : 'npx',
  ['vitest', 'run', 'src/acceptance/roleSmokeLive.spec.ts'],
  { cwd: root, env, stdio: 'inherit', shell: process.platform === 'win32' }
)

process.exit(r.status ?? 1)
