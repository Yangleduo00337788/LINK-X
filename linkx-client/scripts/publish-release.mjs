/**
 * 作者：yangleduo
 */
/**
 * 将 LinkX 安装包上传至 MinIO，并在管理端创建/发布版本记录（启用客户端自动更新）。
 *
 * 用法：
 *   node ./scripts/publish-release.mjs
 *   node ./scripts/publish-release.mjs --file release/installer/LinkX-Installer-1.0.1.exe
 *
 * 环境变量（管理端发布，可选但推荐）：
 *   LINKX_API_BASE_URL   默认 http://127.0.0.1:8080/api
 *   LINKX_ADMIN_USER     管理端账号
 *   LINKX_ADMIN_PASSWORD 管理端密码
 *
 * MinIO 配置默认从 ../linkx-server/.env.local 读取。
 */
import { createHash, randomUUID } from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const rootDir = path.join(__dirname, '..')
const serverEnvPath = path.join(rootDir, '..', 'linkx-server', '.env.local')

const API_SIGN_TIMESTAMP_HEADER = 'X-LinkX-Timestamp'
const API_SIGN_NONCE_HEADER = 'X-LinkX-Nonce'
const API_SIGN_SIGNATURE_HEADER = 'X-LinkX-Signature'

function parseArgs(argv) {
  const args = { file: '', version: '', channel: 'stable', platform: 'windows', publish: true, skipUpload: false }
  for (let i = 2; i < argv.length; i++) {
    const arg = argv[i]
    if (arg === '--file' && argv[i + 1]) args.file = argv[++i]
    else if (arg === '--version' && argv[i + 1]) args.version = argv[++i]
    else if (arg === '--channel' && argv[i + 1]) args.channel = argv[++i]
    else if (arg === '--platform' && argv[i + 1]) args.platform = argv[++i]
    else if (arg === '--no-publish') args.publish = false
    else if (arg === '--skip-upload') args.skipUpload = true
  }
  return args
}

function readEnvFile(filePath) {
  const out = {}
  if (!fs.existsSync(filePath)) return out
  for (const line of fs.readFileSync(filePath, 'utf8').split(/\r?\n/)) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#')) continue
    const idx = trimmed.indexOf('=')
    if (idx <= 0) continue
    const key = trimmed.slice(0, idx).trim()
    let value = trimmed.slice(idx + 1).trim()
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

function sha256File(filePath) {
  const hash = createHash('sha256')
  hash.update(fs.readFileSync(filePath))
  return hash.digest('hex')
}

function buildInstallerObjectName(fileName) {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  return `releases/${y}/${m}/${d}/${fileName}`
}

async function sha256Hex(text) {
  const data = new TextEncoder().encode(text)
  const hash = await crypto.subtle.digest('SHA-256', data)
  return Array.from(new Uint8Array(hash))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

function hexToBytes(hex) {
  const out = new Uint8Array(hex.length / 2)
  for (let i = 0; i < out.length; i++) {
    out[i] = parseInt(hex.substring(i * 2, i * 2 + 2), 16)
  }
  return out
}

async function hmacSha256Hex(keyHex, payload) {
  const keyBytes = hexToBytes(keyHex)
  const cryptoKey = await crypto.subtle.importKey(
    'raw',
    keyBytes.buffer.slice(keyBytes.byteOffset, keyBytes.byteOffset + keyBytes.byteLength),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  )
  const sig = await crypto.subtle.sign('HMAC', cryptoKey, new TextEncoder().encode(payload))
  return Array.from(new Uint8Array(sig))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

async function buildApiSignHeaders(apiSignKeyHex, method, apiPath, bodyText = '') {
  const timestamp = String(Date.now())
  const nonce = randomUUID().replace(/-/g, '')
  const bodyHash = await sha256Hex(bodyText)
  const queryHash = await sha256Hex('')
  const payload = [timestamp, nonce, method.toUpperCase(), apiPath, bodyHash, queryHash].join('\n')
  const signature = await hmacSha256Hex(apiSignKeyHex, payload)
  return {
    [API_SIGN_TIMESTAMP_HEADER]: timestamp,
    [API_SIGN_NONCE_HEADER]: nonce,
    [API_SIGN_SIGNATURE_HEADER]: signature,
  }
}

async function uploadToMinio({ endpoint, accessKey, secretKey, bucket, objectKey, filePath }) {
  const { Client } = await import('minio')
  const url = new URL(endpoint)
  const useSSL = url.protocol === 'https:'
  const port = url.port ? Number(url.port) : useSSL ? 443 : 80
  const client = new Client({
    endPoint: url.hostname,
    port,
    useSSL,
    accessKey,
    secretKey,
  })
  const stat = fs.statSync(filePath)
  await client.fPutObject(bucket, objectKey, filePath, {
    'Content-Type': 'application/octet-stream',
  })
  return stat.size
}

async function adminLogin(apiBase, username, password) {
  const res = await fetch(`${apiBase}/admin/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  const body = await res.json()
  if (!res.ok || body.code !== 200 || !body.data?.accessToken) {
    throw new Error(body.message || `管理端登录失败 HTTP ${res.status}`)
  }
  if (body.data.requiresTotp || body.data.requiresTotpSetup) {
    throw new Error('管理端账号启用了 TOTP，请先在后台关闭或使用未启用 2FA 的账号')
  }
  return {
    accessToken: body.data.accessToken,
    apiSignKey: body.data.apiSignKey || '',
  }
}

async function signedFetch(apiBase, session, method, apiPath, options = {}) {
  const bodyText = options.bodyText ?? ''
  const headers = {
    Accept: 'application/json',
    Authorization: `Bearer ${session.accessToken}`,
    ...(options.headers || {}),
  }
  if (session.apiSignKey) {
    Object.assign(headers, await buildApiSignHeaders(session.apiSignKey, method, apiPath, bodyText))
  }
  const res = await fetch(`${apiBase}${apiPath}`, {
    method,
    headers,
    body: options.body,
  })
  const json = await res.json().catch(() => ({}))
  if (!res.ok || json.code !== 200) {
    throw new Error(json.message || `${method} ${apiPath} 失败 HTTP ${res.status}`)
  }
  return json.data
}

async function createAndPublishVersion(apiBase, session, payload) {
  const bodyText = JSON.stringify(payload)
  const created = await signedFetch(apiBase, session, 'POST', '/admin/versions', {
    body: bodyText,
    bodyText,
    headers: { 'Content-Type': 'application/json' },
  })
  const published = await signedFetch(apiBase, session, 'POST', `/admin/versions/${created.id}/publish`, {
    bodyText: '',
  })
  return published
}

function defaultReleaseNotes(version) {
  return [
    `LinkX ${version} 更新：`,
    '- 灵伴 Agent 代操模式',
    '- Design Token 与 UI 组件体系',
    '- Electron 43 与性能优化',
    '- 消息落库加密（服务端可选）',
  ].join('\n')
}

async function main() {
  const args = parseArgs(process.argv)
  const pkg = JSON.parse(fs.readFileSync(path.join(rootDir, 'package.json'), 'utf8'))
  const version = args.version || pkg.version
  const fileName = `LinkX-Installer-${version}.exe`
  const filePath = path.resolve(rootDir, args.file || path.join('release', 'installer', fileName))

  if (!fs.existsSync(filePath)) {
    console.error(`[publish] 未找到安装包: ${filePath}`)
    console.error('请先执行: npm run electron:build')
    process.exit(1)
  }

  const serverEnv = readEnvFile(serverEnvPath)
  const endpoint = process.env.MINIO_ENDPOINT || serverEnv.MINIO_ENDPOINT
  const accessKey = process.env.MINIO_ACCESS_KEY || serverEnv.MINIO_ACCESS_KEY
  const secretKey = process.env.MINIO_SECRET_KEY || serverEnv.MINIO_SECRET_KEY
  const bucket = process.env.MINIO_BUCKET_NAME || serverEnv.MINIO_BUCKET_NAME || 'linkx'

  if (!endpoint || !accessKey || !secretKey) {
    console.error('[publish] 缺少 MinIO 配置，请检查 linkx-server/.env.local')
    process.exit(1)
  }

  const packageSha256 = sha256File(filePath)
  const objectKey = buildInstallerObjectName(path.basename(filePath))
  const packageSize = fs.statSync(filePath).size

  console.log(`[publish] 安装包: ${filePath}`)
  console.log(`[publish] 大小: ${(packageSize / 1024 / 1024).toFixed(2)} MB`)
  console.log(`[publish] SHA256: ${packageSha256}`)
  console.log(`[publish] 上传到 MinIO: ${endpoint}/${bucket}/${objectKey}`)

  if (!args.skipUpload) {
    await uploadToMinio({ endpoint, accessKey, secretKey, bucket, objectKey, filePath })
    console.log('[publish] MinIO 上传完成')
  } else {
    console.log('[publish] 已跳过 MinIO 上传（--skip-upload）')
  }

  const apiBase = (process.env.LINKX_API_BASE_URL || 'http://127.0.0.1:8080/api').replace(/\/$/, '')
  const adminUser = process.env.LINKX_ADMIN_USER || process.env.E2E_ADMIN_USER
  const adminPassword = process.env.LINKX_ADMIN_PASSWORD || process.env.E2E_ADMIN_PASSWORD

  if (!args.publish) {
    console.log('[publish] 已跳过管理端发布（--no-publish）')
    console.log(`[publish] objectKey: ${objectKey}`)
    return
  }

  if (!adminUser || !adminPassword) {
    console.log('')
    console.log('[publish] 未设置管理端账号，请在管理后台手动发布：')
    console.log('  1. 打开 版本管理 → 新建版本')
    console.log(`  2. 版本号 ${version}，渠道 ${args.channel}，平台 ${args.platform}`)
    console.log(`  3. downloadUrl 填 objectKey: ${objectKey}`)
    console.log(`  4. packageSha256: ${packageSha256}`)
    console.log(`  5. packageFileName: ${path.basename(filePath)}`)
    console.log(`  6. packageSize: ${packageSize}`)
    console.log('  7. 保存草稿 → 发布')
    console.log('')
    console.log('或设置环境变量后重跑：')
    console.log('  $env:LINKX_ADMIN_USER="admin"; $env:LINKX_ADMIN_PASSWORD="***"; node ./scripts/publish-release.mjs')
    return
  }

  console.log(`[publish] 登录管理端 ${apiBase} ...`)
  const session = await adminLogin(apiBase, adminUser, adminPassword)

  const versionPayload = {
    version,
    channel: args.channel,
    platform: args.platform,
    releaseNotes: defaultReleaseNotes(version),
    downloadUrl: objectKey,
    packageSha256,
    packageFileName: path.basename(filePath),
    packageSize,
    forceUpdate: false,
    minSupportedVersion: '',
  }

  console.log('[publish] 创建并发布版本记录 ...')
  const published = await createAndPublishVersion(apiBase, session, versionPayload)
  console.log(`[publish] 版本已发布: id=${published.id}, version=${published.version}, status=${published.status}`)

  const checkUrl = `${apiBase}/app/version?current=1.0.0&channel=${args.channel}&platform=${args.platform}`
  console.log(`[publish] 验证更新接口: ${checkUrl}`)
  const checkRes = await fetch(checkUrl, { headers: { Accept: 'application/json' } })
  const checkBody = await checkRes.json()
  if (checkBody.code === 200 && checkBody.data?.hasUpdate) {
    console.log(`[publish] 自动更新检测通过: latest=${checkBody.data.version}`)
  } else {
    console.warn('[publish] 更新接口未返回 hasUpdate=true，请检查版本记录或渠道/平台参数')
  }
}

main().catch((err) => {
  console.error('[publish] 失败:', err.message || err)
  process.exit(1)
})
