export const API_ENCRYPT_HEADER = 'X-LinkX-Content-Encrypted'
export const API_ENCRYPTED_QUERY_HEADER = 'X-LinkX-Encrypted-Query'

function hexToBytes(hex: string): Uint8Array {
  const normalized = hex.trim()
  const out = new Uint8Array(normalized.length / 2)
  for (let i = 0; i < out.length; i++) {
    out[i] = parseInt(normalized.substring(i * 2, i * 2 + 2), 16)
  }
  return out
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = ''
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]!)
  }
  return btoa(binary)
}

function base64ToBytes(base64: string): Uint8Array {
  const binary = atob(base64.trim())
  const out = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    out[i] = binary.charCodeAt(i)
  }
  return out
}

async function importAesKey(keyHex: string): Promise<CryptoKey> {
  const keyBytes = hexToBytes(keyHex)
  if (keyBytes.length !== 32) {
    throw new Error('invalid api sign key length')
  }
  return crypto.subtle.importKey('raw', keyBytes, { name: 'AES-GCM' }, false, [
    'encrypt',
    'decrypt',
  ])
}

/** 加密 UTF-8 明文为 base64(iv + ciphertext + tag) */
export async function encryptUtf8ToBase64(keyHex: string, plaintext: string): Promise<string> {
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const key = await importAesKey(keyHex)
  const ciphertext = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv, tagLength: 128 },
    key,
    new TextEncoder().encode(plaintext)
  )
  const combined = new Uint8Array(iv.length + ciphertext.byteLength)
  combined.set(iv, 0)
  combined.set(new Uint8Array(ciphertext), iv.length)
  return bytesToBase64(combined)
}

/** 解密 base64(iv + ciphertext + tag) 为 UTF-8 明文 */
export async function decryptUtf8FromBase64(keyHex: string, base64: string): Promise<string> {
  const plain = await decryptToBytes(keyHex, base64)
  return new TextDecoder().decode(plain)
}

/** 解密为原始字节（导出文件等） */
export async function decryptToBytes(keyHex: string, base64: string): Promise<Uint8Array> {
  const combined = base64ToBytes(base64)
  if (combined.length <= 12) {
    throw new Error('invalid ciphertext')
  }
  const iv = combined.slice(0, 12)
  const ciphertext = combined.slice(12)
  const key = await importAesKey(keyHex)
  const plain = await crypto.subtle.decrypt({ name: 'AES-GCM', iv, tagLength: 128 }, key, ciphertext)
  return new Uint8Array(plain)
}

/** 将加密后的 base64 包装为 JSON 字符串字面量，作为请求体/头发送 */
export function wrapEncryptedPayload(base64: string): string {
  return JSON.stringify(base64)
}

export function wrapEncryptedBody(base64: string): string {
  return wrapEncryptedPayload(base64)
}

export function canonicalQueryString(params?: Record<string, unknown>): string {
  if (!params) return ''
  const entries: [string, string][] = []
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null) continue
    entries.push([k, String(v)])
  }
  entries.sort((a, b) => a[0].localeCompare(b[0]))
  return entries
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&')
}

export async function buildEncryptedQueryHeader(
  keyHex: string,
  params?: Record<string, unknown>
): Promise<string> {
  const plain = JSON.stringify(params || {})
  const encrypted = await encryptUtf8ToBase64(keyHex, plain)
  return wrapEncryptedPayload(encrypted)
}

export function isEncryptedResponse(headers: Record<string, unknown>): boolean {
  const raw =
    headers[API_ENCRYPT_HEADER] ??
    headers[API_ENCRYPT_HEADER.toLowerCase()] ??
    headers['x-linkx-content-encrypted']
  return String(raw ?? '').trim() === '1'
}

const AUTH_BOOTSTRAP_PATHS = [
  '/admin/auth/config',
  '/admin/auth/login',
  '/admin/auth/login/totp',
  '/admin/auth/totp/setup-challenge',
  '/admin/auth/totp/confirm-challenge',
  '/admin/auth/refresh',
  '/admin/auth/logout',
  '/auth/captcha',
  '/admin/auth/captcha',
]

export function shouldEncryptRequest(url?: string): boolean {
  if (!url) return false
  const path = url.split('?')[0] || ''
  return !AUTH_BOOTSTRAP_PATHS.some((item) => path.includes(item))
}
