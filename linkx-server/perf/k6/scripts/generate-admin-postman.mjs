#!/usr/bin/env node
/**
 * 从 openapi.json 生成管理端 Postman Collection（兼容旧命令）
 * 输出：docs/admin/linkx-admin.postman_collection.json
 */
import { spawnSync } from 'node:child_process'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const script = join(dirname(fileURLToPath(import.meta.url)), 'generate-postman-collection.mjs')
const result = spawnSync(process.execPath, [script, '--filter', 'admin'], {
  stdio: 'inherit',
})
process.exit(result.status ?? 1)
