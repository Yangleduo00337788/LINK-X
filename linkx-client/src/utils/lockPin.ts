const LOCK_PIN_KEY = 'lockPinHash'
const FALLBACK_LOCK_PIN_KEY = 'linkx:lockPinHash'
const LOCK_PIN_FLAG = 'linkx:lockPinConfigured'

async function digestPin(pin: string): Promise<string> {
  const data = new TextEncoder().encode(pin)
  const hash = await crypto.subtle.digest('SHA-256', data)
  return Array.from(new Uint8Array(hash))
    .map(b => b.toString(16).padStart(2, '0'))
    .join('')
}

async function secureGet(key: string): Promise<string | null> {
  const api = window.electronAPI?.secureStorage
  if (api && (await api.isAvailable())) {
    return api.get(key)
  }
  return localStorage.getItem(FALLBACK_LOCK_PIN_KEY)
}

async function secureSet(key: string, value: string): Promise<void> {
  const api = window.electronAPI?.secureStorage
  if (api && (await api.isAvailable())) {
    await api.set(key, value)
    localStorage.removeItem(FALLBACK_LOCK_PIN_KEY)
    return
  }
  localStorage.setItem(FALLBACK_LOCK_PIN_KEY, value)
}

async function secureRemove(key: string): Promise<void> {
  const api = window.electronAPI?.secureStorage
  if (api) {
    await api.remove(key)
  }
  localStorage.removeItem(FALLBACK_LOCK_PIN_KEY)
}

export async function saveLockPinHash(pin: string): Promise<void> {
  const hash = await digestPin(pin)
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
  const hash = await digestPin(pin)
  return stored === hash
}

export async function clearLockPin(): Promise<void> {
  await secureRemove(LOCK_PIN_KEY)
  localStorage.removeItem(LOCK_PIN_FLAG)
}
