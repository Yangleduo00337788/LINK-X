/** 每个端点打一次，输出状态分布与 5xx 列表 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { fillPath, filterCatalog } from './lib/guards.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const BASE_URL = (process.env.BASE_URL || 'http://127.0.0.1:8080/api').replace(/\/$/, '')
const USER = process.env.USER || process.env.LOAD_USER || ''
const PASS = process.env.PASS || process.env.LOAD_PASS || ''
const INCLUDE_MUTATING = process.env.INCLUDE_MUTATING === '1'

const loginRes = await fetch(`${BASE_URL}/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: USER, password: PASS }),
})
const loginBody = await loginRes.json()
const token = loginBody?.data?.accessToken || ''
if (!token) throw new Error('login failed')

const catalog = JSON.parse(fs.readFileSync(path.join(__dirname, 'endpoints.json'), 'utf8'))
const list = filterCatalog(catalog.endpoints || [], { includeMutating: INCLUDE_MUTATING })

const hist = {}
const serverErrors = []
for (const ep of list) {
  const url = `${BASE_URL}${fillPath(ep.path)}`
  const headers = { Accept: 'application/json', Authorization: `Bearer ${token}` }
  let status = 0
  try {
    const init = { method: ep.method, headers }
    if (ep.method !== 'GET' && ep.method !== 'HEAD') {
      headers['Content-Type'] = 'application/json'
      init.body = '{}'
    }
    const res = await fetch(url, init)
    status = res.status
    await res.arrayBuffer().catch(() => {})
  } catch {
    status = 0
  }
  hist[status] = (hist[status] || 0) + 1
  if (status >= 500 || status === 0) {
    serverErrors.push(`${ep.method} ${ep.path} -> ${status}`)
  }
}

console.log(`scanned=${list.length} mutating=${INCLUDE_MUTATING}`)
console.log('status histogram:', hist)
console.log(`serverErrors=${serverErrors.length}`)
for (const line of serverErrors.slice(0, 40)) console.log(' ', line)
if (serverErrors.length > 40) console.log(`  ... +${serverErrors.length - 40} more`)
