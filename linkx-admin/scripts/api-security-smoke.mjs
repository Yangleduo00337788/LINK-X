#!/usr/bin/env node
/**
 * 管理端 API 签名 + 加密 冒烟测试（直连后端 8080）
 * 环境变量：ADMIN_USER、ADMIN_PASS（必填）、REDIS_HOST/REDIS_PORT/REDIS_PASSWORD（读验证码，可选）
 */
import crypto from 'node:crypto'
import { spawnSync } from 'node:child_process'

const BASE = process.env.API_BASE || 'http://127.0.0.1:8080/api'
const USER = process.env.ADMIN_USER || 'admin'
const PASS = process.env.ADMIN_PASS || ''

async function fetchCaptchaCode(captchaId) {
  if (!captchaId) return ''
  const host = process.env.REDIS_HOST || '127.0.0.1'
  const port = process.env.REDIS_PORT || '6379'
  const password = process.env.REDIS_PASSWORD || ''
  const py = `
import os, sys
try:
    import redis
    r = redis.Redis(host=os.environ.get('REDIS_HOST','127.0.0.1'), port=int(os.environ.get('REDIS_PORT','6379')),
                    password=os.environ.get('REDIS_PASSWORD') or None, db=int(os.environ.get('REDIS_DB','0')), decode_responses=True)
    print(r.get('linkx:captcha:' + sys.argv[1]) or '')
except Exception:
    print('')
`
  const r = spawnSync('python', ['-c', py, captchaId], {
    env: { ...process.env, REDIS_HOST: host, REDIS_PORT: port, REDIS_PASSWORD: password },
    encoding: 'utf8',
  })
  return (r.stdout || '').trim()
}

async function login() {
  if (!PASS) {
    throw new Error('请设置环境变量 ADMIN_PASS')
  }
  const capRes = await fetch(`${BASE}/auth/captcha`)
  const capJson = await capRes.json()
  const captchaId = capJson?.data?.captchaId
  const captchaCode = await fetchCaptchaCode(captchaId)
  const body = {
    username: USER,
    password: PASS,
    ...(captchaId && captchaCode ? { captchaId, captchaCode } : {}),
  }
  const { status, json } = await api('POST', '/admin/auth/login', { body })
  if (status !== 200 || json.code !== 200) {
    throw new Error(`login failed: HTTP ${status} code=${json.code} ${json.message}`)
  }
  return json.data
}

const SIGN_TS = 'X-LinkX-Timestamp'
const SIGN_NONCE = 'X-LinkX-Nonce'
const SIGN_SIG = 'X-LinkX-Signature'
const ENC_HDR = 'X-LinkX-Content-Encrypted'
const ENC_QUERY = 'X-LinkX-Encrypted-Query'

function buildSignHeaders(keyHex, method, path, bodyText = '', querySignMaterial = '') {
  const timestamp = String(Date.now())
  const nonce = crypto.randomUUID().replace(/-/g, '')
  const bodyHash = sha256Hex(bodyText)
  const queryHash = sha256Hex(querySignMaterial)
  const payload = [timestamp, nonce, method.toUpperCase(), normalizePath(path), bodyHash, queryHash].join('\n')
  const signature = hmacSha256Hex(keyHex, payload)
  return {
    [SIGN_TS]: timestamp,
    [SIGN_NONCE]: nonce,
    [SIGN_SIG]: signature,
  }
}

function encryptUtf8ToBase64(keyHex, plaintext) {
  const key = Buffer.from(keyHex, 'hex')
  const iv = crypto.randomBytes(12)
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv)
  const enc = Buffer.concat([cipher.update(plaintext, 'utf8'), cipher.final()])
  const tag = cipher.getAuthTag()
  return Buffer.concat([iv, enc, tag]).toString('base64')
}

function decryptUtf8FromBase64(keyHex, base64) {
  const key = Buffer.from(keyHex, 'hex')
  const combined = Buffer.from(base64, 'base64')
  const iv = combined.subarray(0, 12)
  const tag = combined.subarray(combined.length - 16)
  const ciphertext = combined.subarray(12, combined.length - 16)
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv)
  decipher.setAuthTag(tag)
  return Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString('utf8')
}

function normalizePath(path) {
  const raw = (path.split('?')[0] || '').trim()
  return raw.startsWith('/') ? raw : `/${raw}`
}

function sha256Hex(text) {
  return crypto.createHash('sha256').update(text, 'utf8').digest('hex')
}

function hmacSha256Hex(keyHex, payload) {
  const key = Buffer.from(keyHex, 'hex')
  return crypto.createHmac('sha256', key).update(payload, 'utf8').digest('hex')
}

async function api(method, path, { token, keyHex, body, params, secure = false } = {}) {
  let bodyText = ''
  const headers = { Accept: 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`

  let querySignMaterial = ''
  if (secure && keyHex) {
    const encryptedQuery = JSON.stringify(encryptUtf8ToBase64(keyHex, JSON.stringify(params || {})))
    headers[ENC_HDR] = '1'
    headers[ENC_QUERY] = encryptedQuery
    querySignMaterial = encryptedQuery
  }

  if (body != null) {
    bodyText = typeof body === 'string' ? body : JSON.stringify(body)
    if (secure && keyHex) {
      bodyText = JSON.stringify(encryptUtf8ToBase64(keyHex, bodyText))
      headers['Content-Type'] = 'application/json'
    } else {
      headers['Content-Type'] = 'application/json'
    }
  }

  if (keyHex && token) {
    Object.assign(headers, buildSignHeaders(keyHex, method, path, bodyText, querySignMaterial))
  }

  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body != null ? bodyText : undefined,
  })
  const enc = res.headers.get(ENC_HDR) === '1'
  const json = await res.json()
  if (enc && keyHex && json?.data && typeof json.data === 'string') {
    json.data = JSON.parse(decryptUtf8FromBase64(keyHex, json.data))
  }
  return { status: res.status, enc, json }
}

function assert(cond, msg) {
  if (!cond) throw new Error(msg)
}

async function main() {
  const results = []

  async function step(name, fn) {
    try {
      await fn()
      results.push({ name, ok: true })
      console.log(`✓ ${name}`)
    } catch (e) {
      results.push({ name, ok: false, error: e.message })
      console.error(`✗ ${name}: ${e.message}`)
    }
  }

  let token = ''
  let keyHex = ''
  let encryptOn = false

  await step('GET /admin/auth/config', async () => {
    const { status, json } = await api('GET', '/admin/auth/config')
    assert(status === 200, `HTTP ${status}`)
    assert(json.code === 200, `code=${json.code} ${json.message}`)
    assert(json.data?.apiSignEnabled === true, 'apiSignEnabled should be true')
    encryptOn = json.data?.apiEncryptEnabled === true
    console.log(`  sign=${json.data?.apiSignEnabled} encrypt=${json.data?.apiEncryptEnabled}`)
  })

  await step('POST /admin/auth/login', async () => {
    const data = await login()
    assert(data?.accessToken, 'missing accessToken')
    assert(data?.apiSignKey, 'missing apiSignKey')
    token = data.accessToken
    keyHex = data.apiSignKey
  })

  await step('GET /admin/auth/me (signed)', async () => {
    const { status, json, enc } = await api('GET', '/admin/auth/me', { token, keyHex, secure: encryptOn })
    assert(status === 200, `HTTP ${status}`)
    assert(json.code === 200, `code=${json.code} ${json.message}`)
    assert(json.data?.username, 'missing username in profile')
    if (encryptOn) assert(enc, 'response should be encrypted')
    else assert(!enc, 'response should be plaintext when encrypt off')
  })

  await step('GET /admin/settings (signed)', async () => {
    const { status, json } = await api('GET', '/admin/settings', { token, keyHex, secure: encryptOn })
    assert(status === 200, `HTTP ${status}`)
    assert(json.code === 200, `code=${json.code} ${json.message}`)
    assert(json.data?.security != null, 'missing security settings')
    console.log(
      `  server security: sign=${json.data.security.apiSignEnabled} encrypt=${json.data.security.apiEncryptEnabled}`
    )
  })

  await step('POST /admin/auth/step-up/verify (encrypted)', async () => {
    const { status, json } = await api('POST', '/admin/auth/step-up/verify', {
      token,
      keyHex,
      body: { method: 'totp', code: '000000', action: 'test' },
      secure: encryptOn,
    })
    assert(status === 400 || json.code === 400, `expected business error, HTTP ${status} code=${json.code}`)
    assert(
      !String(json.message || '').includes('解密') &&
        !String(json.message || '').includes('签名') &&
        !String(json.message || '').includes('encrypt'),
      `infra error: ${json.message}`
    )
  })

  if (encryptOn) {
    await step('GET /admin/auth/menus encrypted response', async () => {
      const { status, json, enc } = await api('GET', '/admin/auth/menus', { token, keyHex, secure: true })
      assert(status === 200, `HTTP ${status}`)
      assert(json.code === 200, `code=${json.code} ${json.message}`)
      assert(enc, 'menus response should be encrypted')
      assert(Array.isArray(json.data), 'menus should decrypt to array')
    })
  }

  const failed = results.filter((r) => !r.ok)
  console.log(`\n${results.length - failed.length}/${results.length} passed`)
  if (failed.length) {
    process.exit(1)
  }
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
