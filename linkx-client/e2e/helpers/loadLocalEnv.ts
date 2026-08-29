/**
 * 从 linkx-server/.env.local 加载 E2E 变量（不提交 git）。
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const e2eDir = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(e2eDir, '../../..')
const envLocalPath = path.join(repoRoot, 'linkx-server', '.env.local')

function parseDotEnv(content: string): Record<string, string> {
  const out: Record<string, string> = {}
  for (const line of content.split(/\r?\n/)) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#')) continue
    const eq = trimmed.indexOf('=')
    if (eq <= 0) continue
    const key = trimmed.slice(0, eq).trim()
    let value = trimmed.slice(eq + 1).trim()
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1)
    }
    out[key] = value
  }
  return out
}

export function loadLocalEnv(): void {
  if (!fs.existsSync(envLocalPath)) return
  const parsed = parseDotEnv(fs.readFileSync(envLocalPath, 'utf8'))
  for (const [key, value] of Object.entries(parsed)) {
    if (key.startsWith('E2E_') && !process.env[key]) {
      process.env[key] = value
    }
  }
  if (!process.env.E2E_API_BASE_URL && parsed.SERVER_PORT) {
    process.env.E2E_API_BASE_URL = `http://127.0.0.1:${parsed.SERVER_PORT}`
  }
}

loadLocalEnv()
