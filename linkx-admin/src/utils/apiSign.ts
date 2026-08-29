/**
 * 作者：yangleduo
 */
export const API_SIGN_TIMESTAMP_HEADER = 'X-LinkX-Timestamp'
export const API_SIGN_NONCE_HEADER = 'X-LinkX-Nonce'
export const API_SIGN_SIGNATURE_HEADER = 'X-LinkX-Signature'

const EXCLUDED_PATHS = [
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

export function shouldSignRequest(url?: string): boolean {
  if (!url) return false
  const path = url.split('?')[0] || ''
  return !EXCLUDED_PATHS.some((item) => path.includes(item))
}

function normalizePath(path: string): string {
  const raw = (path.split('?')[0] || '').trim()
  const withSlash = raw.startsWith('/') ? raw : `/${raw}`
  return withSlash || '/'
}

function hexToBytes(hex: string): Uint8Array {
  const normalized = hex.trim()
  const out = new Uint8Array(normalized.length / 2)
  for (let i = 0; i < out.length; i++) {
    out[i] = parseInt(normalized.substring(i * 2, i * 2 + 2), 16)
  }
  return out
}

async function sha256Hex(text: string): Promise<string> {
  const data = new TextEncoder().encode(text)
  const hash = await crypto.subtle.digest('SHA-256', data)
  return Array.from(new Uint8Array(hash))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

function bufferSource(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer
}

async function hmacSha256Hex(keyHex: string, payload: string): Promise<string> {
  const keyBytes = hexToBytes(keyHex)
  const cryptoKey = await crypto.subtle.importKey(
    'raw',
    bufferSource(keyBytes),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  )
  const sig = await crypto.subtle.sign('HMAC', cryptoKey, new TextEncoder().encode(payload))
  return Array.from(new Uint8Array(sig))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

export async function buildApiSignHeaders(
  apiSignKeyHex: string,
  method: string,
  path: string,
  bodyText = '',
  querySignMaterial = ''
): Promise<Record<string, string>> {
  const timestamp = String(Date.now())
  const nonce =
    typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID().replace(/-/g, '')
      : `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
  const bodyHash = await sha256Hex(bodyText)
  const queryHash = await sha256Hex(querySignMaterial)
  const payload = [
    timestamp,
    nonce,
    method.toUpperCase(),
    normalizePath(path),
    bodyHash,
    queryHash,
  ].join('\n')
  const signature = await hmacSha256Hex(apiSignKeyHex, payload)
  return {
    [API_SIGN_TIMESTAMP_HEADER]: timestamp,
    [API_SIGN_NONCE_HEADER]: nonce,
    [API_SIGN_SIGNATURE_HEADER]: signature,
  }
}
