#!/usr/bin/env node
/**
 * 本地冒烟：对齐 linkx-smoke-scenarios，自动注册客户端用户
 * 管理端账号从环境变量读取：ADMIN_USER / ADMIN_PASS
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const base = process.env.BASE_URL || 'http://localhost:8080/api'
const adminUser = process.env.ADMIN_USER || process.env.adminUsername || 'admin'
const adminPass = process.env.ADMIN_PASS || process.env.adminPassword || ''
const clientUser = process.env.CLIENT_USER || process.env.clientUsername || `smoke_${Date.now()}`
const clientPass = process.env.CLIENT_PASS || process.env.clientPassword || 'Smoke12345'

const smokePaths = JSON.parse(
  fs.readFileSync(path.join(__dirname, '../../linkx-server/perf/k6/scripts/smoke-paths.json'), 'utf8'),
)

let clientToken = ''
let adminToken = ''
const results = []

async function req(method, urlPath, { token, body } = {}) {
  const headers = { Accept: 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (body != null) headers['Content-Type'] = 'application/json'
  const init = { method, headers }
  if (body != null) init.body = JSON.stringify(body)
  const res = await fetch(`${base}${urlPath}`, init)
  const text = await res.text()
  let json
  try {
    json = JSON.parse(text)
  } catch {
    json = { raw: text.slice(0, 200) }
  }
  return { status: res.status, json }
}

function record(group, path, method, ok, detail) {
  results.push({ group, method, path, ok, detail })
  const mark = ok ? '✓' : '✗'
  console.log(`${mark} [${group}] ${method} ${path}${detail ? ' — ' + detail : ''}`)
}

async function login(path, user, pass) {
  const r = await req('POST', path, { body: { username: user, password: pass } })
  if (r.status === 200 && r.json?.code === 200 && r.json?.data?.accessToken) {
    return r.json.data.accessToken
  }
  return null
}

async function registerClient() {
  const username = clientUser
  const password = clientPass
  const r = await req('POST', '/auth/register', { body: { username, password, nickname: 'smoke' } })
  if (r.status === 200 && r.json?.code === 200) return { username, password }
  if (r.json?.message?.includes('已存在') || r.json?.message?.includes('exist')) {
    return { username, password }
  }
  throw new Error(`注册客户端失败: ${r.status} ${JSON.stringify(r.json)}`)
}

async function runGets(group, paths, token, expectOk = true) {
  for (const p of paths) {
    const concrete = p.replace(/\{[^}]+\}/g, '1')
    const r = await req('GET', concrete, { token })
    const ok = expectOk ? r.status === 200 && r.json?.code === 200 : r.status < 500
    record(group, concrete, 'GET', ok, expectOk ? '' : `status=${r.status}`)
  }
}

async function main() {
  console.log(`BASE_URL=${base}`)
  console.log('--- 01 客户端公开读 ---')
  await runGets('01-public', smokePaths.clientPublicGet, null, true)

  console.log('--- 02 客户端登录后读 ---')
  const cred = await registerClient()
  clientToken = await login('/auth/login', cred.username, cred.password)
  if (!clientToken) throw new Error('客户端登录失败')
  console.log(`客户端用户: ${cred.username}`)
  await runGets('02-auth', smokePaths.clientAuthGet, clientToken, true)

  console.log('--- 03 客户端热路径 ---')
  await runGets('03-hot', smokePaths.clientHotPathGet, clientToken, true)

  console.log('--- 04 管理端登录后核心 ---')
  if (!adminPass) {
    console.log('⚠ 跳过管理端冒烟：未设置 ADMIN_PASS / adminPassword')
  } else {
    adminToken = await login('/admin/auth/login', adminUser, adminPass)
    if (!adminToken) throw new Error('管理端登录失败，请检查 ADMIN_PASS')
    await runGets('04-admin', smokePaths.adminSmokeAfterLogin || [], adminToken, true)
  }

  console.log('--- 05 管理端目录扫 ---')
  if (adminToken) {
    await runGets('05-catalog', smokePaths.adminCatalogGet || [], adminToken, false)
  } else {
    console.log('⚠ 跳过管理端目录扫')
  }

  const failed = results.filter((r) => !r.ok)
  console.log(`\n=== 完成 ${results.length - failed.length}/${results.length} 通过 ===`)
  if (failed.length) {
    console.log('失败项:')
    for (const f of failed) console.log(`  ${f.method} ${f.path} (${f.detail})`)
    process.exit(1)
  }
}

main().catch((e) => {
  console.error(e.message)
  process.exit(1)
})
