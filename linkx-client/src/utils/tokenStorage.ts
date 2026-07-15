const ACCESS_KEY = 'accessToken'
const REFRESH_KEY = 'refreshToken'

/**
 * Web 浏览器下使用 sessionStorage（关闭标签即清）
 * Electron 桌面应用使用 Electron 安全存储（keychain/Windows Credential Vault）
 */
const FALLBACK_PREFIX = 'linkx:session-token:'

type TokenKey = typeof ACCESS_KEY | typeof REFRESH_KEY

let secureStorageAvailable: boolean | null = null

function fallbackKey(key: TokenKey): string {
  return FALLBACK_PREFIX + key
}

/**
 * 在 Web 环境下使用 sessionStorage（不是 localStorage）保存 token，
 * 浏览器关闭时自动清除，避免长期明文驻留。
 */
function webStorage(): Storage | null {
  try {
    return typeof sessionStorage !== 'undefined' ? sessionStorage : null
  } catch {
    return null
  }
}

async function isSecureStorageAvailable(): Promise<boolean> {
  if (secureStorageAvailable !== null) return secureStorageAvailable
  const api = window.electronAPI?.secureStorage
  if (!api) {
    secureStorageAvailable = false
    return false
  }
  secureStorageAvailable = await api.isAvailable()
  return secureStorageAvailable
}

async function secureGet(key: string): Promise<string | null> {
  // 1. 优先 Electron 安全存储
  const api = window.electronAPI?.secureStorage
  if (api && (await isSecureStorageAvailable())) {
    const v = await api.get(key)
    if (v) return v
  }
  // 2. Web 浏览器：sessionStorage 临时存储（不再使用 localStorage）
  const ws = webStorage()
  if (ws) {
    return ws.getItem(fallbackKey(key as TokenKey))
  }
  return null
}

async function secureSet(key: string, value: string): Promise<void> {
  const api = window.electronAPI?.secureStorage
  if (api && (await isSecureStorageAvailable())) {
    await api.set(key, value)
    // 清理可能残留的 localStorage 数据（一次性清理历史明文残留）
    try {
      localStorage.removeItem(fallbackKey(key as TokenKey))
    } catch {
      // ignore
    }
    return
  }
  // Web 环境：sessionStorage（关闭标签即清）
  const ws = webStorage()
  if (ws) {
    ws.setItem(fallbackKey(key as TokenKey), value)
  }
}

async function secureRemove(key: string): Promise<void> {
  const api = window.electronAPI?.secureStorage
  if (api) {
    try {
      await api.remove(key)
    } catch {
      // ignore
    }
  }
  try {
    sessionStorage.removeItem(fallbackKey(key as TokenKey))
    // 兼顾清理历史 localStorage 残留
    localStorage.removeItem(fallbackKey(key as TokenKey))
  } catch {
    // ignore
  }
}

/**
 * 启动时清理历史 localStorage 中可能残留的 token
 * （因为之前用 localStorage 临时保存过，避免敏感数据长期驻留）
 */
export function purgeLegacyTokens() {
  try {
    localStorage.removeItem(fallbackKey(ACCESS_KEY))
    localStorage.removeItem(fallbackKey(REFRESH_KEY))
  } catch {
    // ignore
  }
}

export async function getToken(key: TokenKey): Promise<string | null> {
  return secureGet(key)
}

export async function setToken(key: TokenKey, value: string): Promise<void> {
  await secureSet(key, value)
}

export async function getRefreshToken(): Promise<string | null> {
  return getToken(REFRESH_KEY)
}

export async function hasRefreshToken(): Promise<boolean> {
  const ws = webStorage()
  if (ws && ws.getItem(fallbackKey(REFRESH_KEY))) return true
  return !!(await getRefreshToken())
}

export async function saveTokenPair(accessToken: string, refreshToken: string): Promise<void> {
  await secureSet(ACCESS_KEY, accessToken)
  await secureSet(REFRESH_KEY, refreshToken)
}

export async function clearTokens(): Promise<void> {
  await secureRemove(ACCESS_KEY)
  await secureRemove(REFRESH_KEY)
}
