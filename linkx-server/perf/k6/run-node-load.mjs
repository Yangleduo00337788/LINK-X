/**
 * Node 压测运行器（本机无 k6 / 无法下载时的等价替代）
 * 用法:
 *   node run-node-load.mjs hot-path
 *   node run-node-load.mjs full-api
 *
 * 环境变量: BASE_URL USER PASS VUS DURATION INCLUDE_MUTATING MAX_ENDPOINTS
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { fillPath, filterCatalog } from './lib/guards.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const BASE_URL = (process.env.BASE_URL || 'http://127.0.0.1:8080/api').replace(/\/$/, '')
const USER = process.env.USER || process.env.LOAD_USER || ''
const PASS = process.env.PASS || process.env.LOAD_PASS || ''
const VUS = Number(process.env.VUS || 5)
const DURATION_MS = parseDuration(process.env.DURATION || '30s')
const INCLUDE_MUTATING = process.env.INCLUDE_MUTATING === '1'
const MAX_ENDPOINTS = Number(process.env.MAX_ENDPOINTS || 0)
const mode = process.argv[2] || 'hot-path'

function parseDuration(s) {
  const m = String(s).match(/^(\d+)(ms|s|m)?$/)
  if (!m) return 30000
  const n = Number(m[1])
  const u = m[2] || 's'
  if (u === 'ms') return n
  if (u === 'm') return n * 60_000
  return n * 1000
}

async function login() {
  if (!USER || !PASS) throw new Error('Set USER/PASS (or LOAD_USER/LOAD_PASS)')
  const res = await fetch(`${BASE_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USER, password: PASS }),
  })
  const body = await res.json()
  const token = body?.data?.accessToken || ''
  const refresh = body?.data?.refreshToken || ''
  if (!token) throw new Error(`login failed: ${JSON.stringify(body)}`)
  return { token, refresh }
}

function stats() {
  return { total: 0, ok: 0, fail: 0, statuses: {}, latencies: [] }
}

function record(st, status, ms) {
  st.total++
  st.latencies.push(ms)
  st.statuses[status] = (st.statuses[status] || 0) + 1
  if (status > 0 && status < 500) st.ok++
  else st.fail++
}

function summarize(name, st, elapsedMs) {
  const sorted = [...st.latencies].sort((a, b) => a - b)
  const p = (q) => (sorted.length ? sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * q))] : 0)
  const rps = elapsedMs > 0 ? ((st.total / elapsedMs) * 1000).toFixed(1) : '0'
  const failRate = st.total ? ((st.fail / st.total) * 100).toFixed(2) : '0'
  console.log(`\n=== ${name} ===`)
  console.log(`requests=${st.total} ok(<500)=${st.ok} fail(5xx/net)=${st.fail} failRate=${failRate}%`)
  console.log(`rps≈${rps} p50=${p(0.5)}ms p95=${p(0.95)}ms p99=${p(0.99)}ms max=${sorted.at(-1) || 0}ms`)
  console.log('status histogram:', st.statuses)
  return { failRate: Number(failRate), p95: p(0.95) }
}

async function request(method, url, { headers, body } = {}) {
  const t0 = Date.now()
  try {
    const res = await fetch(url, { method, headers, body })
    await res.arrayBuffer().catch(() => {})
    return { status: res.status, ms: Date.now() - t0 }
  } catch {
    return { status: 0, ms: Date.now() - t0 }
  }
}

async function runHotPath() {
  const auth = await login()
  const st = stats()
  const end = Date.now() + DURATION_MS
  const workers = Array.from({ length: VUS }, async () => {
    while (Date.now() < end) {
      {
        const r = await request('GET', `${BASE_URL}/health`)
        record(st, r.status, r.ms)
      }
      const headers = { Authorization: `Bearer ${auth.token}`, Accept: 'application/json' }
      for (const p of ['/chat/sessions', '/friend/list', '/group/list', '/user/me']) {
        const r = await request('GET', `${BASE_URL}${p}`, { headers })
        record(st, r.status, r.ms)
      }
      if (auth.refresh) {
        const r = await request('POST', `${BASE_URL}/auth/refresh`, {
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ refreshToken: auth.refresh }),
        })
        record(st, r.status, r.ms)
      }
    }
  })
  const t0 = Date.now()
  await Promise.all(workers)
  return summarize('hot-path', st, Date.now() - t0)
}

async function runFullApi() {
  const auth = await login()
  let catalog
  try {
    catalog = JSON.parse(fs.readFileSync(path.join(__dirname, 'endpoints.json'), 'utf8'))
  } catch {
    catalog = JSON.parse(fs.readFileSync(path.join(__dirname, 'endpoints.sample.json'), 'utf8'))
  }
  let list = filterCatalog(catalog.endpoints || [], { includeMutating: INCLUDE_MUTATING })
  if (MAX_ENDPOINTS > 0) list = list.slice(0, MAX_ENDPOINTS)
  if (!list.length) throw new Error('no endpoints')

  console.log(`full-api endpoints=${list.length} mutating=${INCLUDE_MUTATING} vus=${VUS} duration=${DURATION_MS}ms`)
  const st = stats()
  const end = Date.now() + DURATION_MS
  let idx = 0
  const workers = Array.from({ length: VUS }, async () => {
    while (Date.now() < end) {
      const ep = list[idx++ % list.length]
      const url = `${BASE_URL}${fillPath(ep.path)}`
      const headers = { Accept: 'application/json' }
      if (auth.token && ep.security !== false) headers.Authorization = `Bearer ${auth.token}`
      let r
      if (ep.method === 'GET' || ep.method === 'HEAD') {
        r = await request(ep.method, url, { headers })
      } else {
        r = await request(ep.method, url, {
          headers: { ...headers, 'Content-Type': 'application/json' },
          body: '{}',
        })
      }
      record(st, r.status, r.ms)
    }
  })
  const t0 = Date.now()
  await Promise.all(workers)
  return summarize('full-api', st, Date.now() - t0)
}

const result = mode === 'full-api' ? await runFullApi() : await runHotPath()
if (result.failRate > 5) {
  console.error('FAIL: failRate > 5%')
  process.exit(1)
}
console.log('\nLoad run finished OK')
