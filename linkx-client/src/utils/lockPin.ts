const LOCK_PIN_KEY = 'lockPinHash'
const FALLBACK_LOCK_PIN_KEY = 'linkx:lockPinHash'
const LOCK_PIN_FLAG = 'linkx:lockPinConfigured'

// PBKDF2 参数
const PBKDF2_ITERATIONS = 100000
const SALT_LENGTH = 16

async function generateSalt(): Promise<Uint8Array> {
  const salt = new Uint8Array(SALT_LENGTH)
  if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
    crypto.getRandomValues(salt)
  } else {
    for (let i = 0; i < SALT_LENGTH; i++) {
      salt[i] = Math.floor(Math.random() * 256)
    }
  }
  return salt
}

async function deriveKey(pin: string, salt: Uint8Array): Promise<string> {
  const encoder = new TextEncoder()
  const pinBuffer = encoder.encode(pin)

  // 使用 PBKDF2 派生密钥
  const keyMaterial = await crypto.subtle.importKey(
    'raw',
    pinBuffer,
    'PBKDF2',
    false,
    ['deriveBits']
  )

  const derivedBits = await crypto.subtle.deriveBits(
    {
      name: 'PBKDF2',
      salt: salt,
      iterations: PBKDF2_ITERATIONS,
      hash: 'SHA-256'
    },
    keyMaterial,
    256
  )

  // 将 salt 和 hash 组合返回（salt:hash）
  const saltArray = Array.from(salt)
  const hashArray = Array.from(new Uint8Array(derivedBits))
  const combined = [...saltArray, ...hashArray]
  return combined.map(b => b.toString(16).padStart(2, '0')).join('')
}

async function secureGet(key: string): Promise<string | null> {
  const api = window.electronAPI?.secureStorage
  if (api && (await api.isAvailable())) {
    return api.get(key)
  }
  // 安全增强：不允许明文回退到 localStorage
  return null
}

async function secureSet(key: string, value: string): Promise<void> {
  const api = window.electronAPI?.secureStorage
  if (api && (await api.isAvailable())) {
    await api.set(key, value)
    // 清理可能的残留
    localStorage.removeItem(FALLBACK_LOCK_PIN_KEY)
    return
  }
  // 安全增强：不允许明文回退到 localStorage
  throw new Error('安全存储不可用，无法保存 PIN')
}

async function secureRemove(key: string): Promise<void> {
  const api = window.electronAPI?.secureStorage
  if (api) {
    await api.remove(key)
  }
  localStorage.removeItem(FALLBACK_LOCK_PIN_KEY)
}

export async function saveLockPinHash(pin: string): Promise<void> {
  const salt = await generateSalt()
  const hash = await deriveKey(pin, salt)
  await secureSet(LOCK_PIN_KEY, hash)
  localStorage.setItem(LOCK_PIN_FLAG, '1')
}

export function hasLockPin(): boolean {
  return localStorage.getItem(LOCK_PIN_FLAG) === '1'
}

export async function verifyLockPin(pin: string): Promise<boolean> {
  const stored = await secureGet(LOCK_PIN_KEY)
  if (!stored) {
    return false
  }

  // 解析存储的 salt:hash 格式
  const storedHex = stored.replace(/^0x/, '')
  const saltHex = storedHex.substring(0, SALT_LENGTH * 2)
  const storedHash = storedHex.substring(SALT_LENGTH * 2)

  const salt = new Uint8Array(SALT_LENGTH)
  for (let i = 0; i < SALT_LENGTH; i++) {
    salt[i] = parseInt(saltHex.substring(i * 2, i * 2 + 2), 16)
  }

  const hash = await deriveKey(pin, salt)
  // 比较时去掉 salt 部分
  return hash.substring(SALT_LENGTH * 2) === storedHash
}

export async function clearLockPin(): Promise<void> {
  await secureRemove(LOCK_PIN_KEY)
  localStorage.removeItem(LOCK_PIN_FLAG)
}
