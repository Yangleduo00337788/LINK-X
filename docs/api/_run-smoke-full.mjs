#!/usr/bin/env node
/**
 * 完整冒烟：自动准备账号 + newman 执行
 * 管理端临时密码：SmokeAdmin@123（仅本地冒烟，会写回 admin 账号）
 */
import fs from 'node:fs'
import { spawnSync } from 'node:child_process'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const base = process.env.BASE_URL || 'http://localhost:8080/api'
const SMOKE_CLIENT_USER = 'smoke_apifox'
const SMOKE_CLIENT_PASS = 'SmokeClient@123'
const SMOKE_ADMIN_PASS = process.env.ADMIN_PASS || 'SmokeAdmin@123'
const SMOKE_EMAIL = 'smoke_apifox@linkx.local'

function loadEnvLocal() {
  const env = {}
  const p = 'linkx-server/.env.local'
  if (!fs.existsSync(p)) return env
  for (const line of fs.readFileSync(p, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^([^#=]+)=(.*)$/)
    if (m) env[m[1].trim()] = m[2].trim()
  }
  return env
}

async function api(method, path, body) {
  const r = await fetch(base + path, {
    method,
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: body != null ? JSON.stringify(body) : undefined,
  })
  return { status: r.status, json: await r.json().catch(() => ({})) }
}

function redisGet(key) {
  const env = loadEnvLocal()
  const pass = env.REDIS_PASSWORD
  const tryDocker = spawnSync('docker', ['exec', 'redis', 'redis-cli', ...(pass ? ['-a', pass] : []), 'GET', key], {
    encoding: 'utf8',
  })
  if (tryDocker.status === 0) return (tryDocker.stdout || '').trim().replace(/^"|"$/g, '')
  const tryLocal = spawnSync(
    'redis-cli',
    ['-h', '127.0.0.1', '-p', '6379', ...(pass ? ['-a', pass] : []), 'GET', key],
    { encoding: 'utf8', shell: true },
  )
  if (tryLocal.status !== 0) throw new Error(`Redis GET 失败: ${tryLocal.stderr || tryDocker.stderr}`)
  return (tryLocal.stdout || '').trim().replace(/^"|"$/g, '')
}

async function ensureClientUser() {
  let login = await api('POST', '/auth/login', { username: SMOKE_CLIENT_USER, password: SMOKE_CLIENT_PASS })
  if (login.status === 200 && login.json?.code === 200) {
    console.log('客户端账号已存在，直接登录')
    return
  }
  console.log('创建客户端冒烟账号...')
  await api('POST', '/auth/send-register-code', { email: SMOKE_EMAIL, username: SMOKE_CLIENT_USER })
  const code = redisGet(`linkx:register-email:${SMOKE_EMAIL}`)
  if (!code) throw new Error('无法从 Redis 读取注册验证码，请确认 Redis 可用')
  const reg = await api('POST', '/auth/register', {
    username: SMOKE_CLIENT_USER,
    password: SMOKE_CLIENT_PASS,
    nickname: 'smoke',
    email: SMOKE_EMAIL,
    emailCode: code,
  })
  if (reg.status !== 200 || reg.json?.code !== 200) {
    throw new Error(`注册失败: ${JSON.stringify(reg.json)}`)
  }
  console.log('客户端账号创建成功')
}

function ensureAdminPassword() {
  if (process.env.ADMIN_PASS) {
    console.log('使用环境变量 ADMIN_PASS')
    return
  }
  let bcrypt
  try {
    bcrypt = require('bcryptjs')
  } catch {
    console.log('安装 bcryptjs...')
    spawnSync('npm', ['install', '--no-save', 'bcryptjs'], { cwd: 'docs/api', stdio: 'inherit', shell: true })
    bcrypt = require('bcryptjs')
  }
  const hash = bcrypt.hashSync(SMOKE_ADMIN_PASS, 10)
  const env = loadEnvLocal()
  const db = (env.DB_URL.match(/\/([^\/?]+)(\?|$)/) || [])[1] || 'linkx'
  const sql = `UPDATE sys_user SET password='${hash}' WHERE username='admin' AND deleted=0 LIMIT 1`
  const r = spawnSync(
    'mysql',
    ['-h', '127.0.0.1', '-P', '3306', '-u', env.DB_USERNAME, `-p${env.DB_PASSWORD}`, db, '-e', sql],
    { encoding: 'utf8' },
  )
  if (r.status !== 0) throw new Error(`更新 admin 密码失败: ${r.stderr}`)
  console.log(`已设置 admin 临时密码（本地冒烟）`)
}

async function runNewman() {
  const args = [
    'newman',
    'run',
    'docs/api/linkx-smoke-scenarios.postman_collection.json',
    '--env-var',
    `baseUrl=${base}`,
    '--env-var',
    `clientUsername=${SMOKE_CLIENT_USER}`,
    '--env-var',
    `clientPassword=${SMOKE_CLIENT_PASS}`,
    '--env-var',
    'adminUsername=admin',
    '--env-var',
    `adminPassword=${SMOKE_ADMIN_PASS}`,
    '--reporters',
    'cli,json',
    '--reporter-json-export',
    'docs/api/apifox-reports/smoke-result.json',
  ]
  const r = spawnSync('npx', ['--yes', ...args], { stdio: 'inherit', shell: true, cwd: process.cwd() })
  return r.status === 0
}

async function main() {
  console.log('>>> 准备冒烟账号')
  await ensureClientUser()
  ensureAdminPassword()
  console.log('\n>>> 运行 LinkX Smoke Scenarios (newman)')
  const ok = await runNewman()
  process.exit(ok ? 0 : 1)
}

main().catch((e) => {
  console.error(e.message)
  process.exit(1)
})
