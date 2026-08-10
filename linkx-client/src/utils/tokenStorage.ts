/**
 * 作者：yangleduo
 */
/**
 * @security Web 环境 Token 存储安全说明
 *
 * Web 浏览器环境：Access/Refresh Token 由后端通过 HttpOnly + Secure + SameSite=Lax 的 Cookie 管理，
 * JS 无法读取 HttpOnly Cookie，从根本上规避 XSS 窃取 token。本模块在 Web 环境下所有读写均为 no-op：
 * - getAccessToken / getRefreshToken 返回 null（让请求拦截器不带 Authorization Header，依赖 Cookie）
 * - setToken / saveTokenPair / clearTokens 为空操作（Cookie 由后端 login/refresh/logout 接口管理）
 *
 * Electron 桌面环境：继续使用 safeStorage 加密落盘（keychain / Windows Credential Vault）+ Authorization Header，
 * 不存明文，本模块仅对 Electron 环境生效。
 */
const ACCESS_KEY = 'accessToken'
const REFRESH_KEY = 'refreshToken'

/**
 * Web 浏览器下历史遗留的 sessionStorage 临时存储 key（仅用于一次性清理历史明文残留）
 */
const FALLBACK_PREFIX = 'linkx:session-token:'

type TokenKey = typeof ACCESS_KEY | typeof REFRESH_KEY

let secureStorageAvailable: boolean | null = null

function fallbackKey(key: TokenKey): string {
  return FALLBACK_PREFIX + key
}

/**
 * 是否为 Web 浏览器环境（非 Electron）。
 * Web 环境 token 由后端 HttpOnly Cookie 管理；Electron 环境走 safeStorage + Authorization Header。
 * 判定依据：preload 注入的 window.electronAPI.secureStorage 是否存在。
 */
export function isWebEnvironment(): boolean {
  return !window.electronAPI?.secureStorage
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
  // Web 环境：token 在 HttpOnly Cookie 中，JS 不可读，直接返回 null
  if (isWebEnvironment()) {
    return null
  }
  // Electron 环境：优先 safeStorage 加密存储
  const api = window.electronAPI?.secureStorage
  if (api && (await isSecureStorageAvailable())) {
    return api.get(key)
  }
  return null
}

async function secureSet(key: string, _value: string): Promise<void> {
  // Web 环境：no-op，token 由后端 Set-Cookie 管理
  if (isWebEnvironment()) {
    return
  }
  // Electron 环境：写入 safeStorage 加密存储
  const api = window.electronAPI?.secureStorage
  if (api && (await isSecureStorageAvailable())) {
    await api.set(key, _value)
    // 清理可能残留的历史 sessionStorage/localStorage 数据（一次性清理历史明文残留）
    try {
      sessionStorage.removeItem(fallbackKey(key as TokenKey))
      localStorage.removeItem(fallbackKey(key as TokenKey))
    } catch {
      // ignore
    }
  }
}

async function secureRemove(key: string): Promise<void> {
  // Web 环境：no-op，Cookie 由后端 logout 接口清除（HttpOnly Cookie JS 无法主动删除）
  if (isWebEnvironment()) {
    return
  }
  // Electron 环境：从 safeStorage 移除
  const api = window.electronAPI?.secureStorage
  if (api) {
    try {
      await api.remove(key)
    } catch {
      // ignore
    }
  }
  // 兼顾清理历史 sessionStorage/localStorage 残留
  try {
    sessionStorage.removeItem(fallbackKey(key as TokenKey))
    localStorage.removeItem(fallbackKey(key as TokenKey))
  } catch {
    // ignore
  }
}

/**
 * 启动时清理历史 sessionStorage/localStorage 中可能残留的 token
 * （之前用 sessionStorage 临时保存过，迁移到 HttpOnly Cookie 后避免敏感数据驻留）
 */
export function purgeLegacyTokens() {
  try {
    sessionStorage.removeItem(fallbackKey(ACCESS_KEY))
    sessionStorage.removeItem(fallbackKey(REFRESH_KEY))
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

/**
 * 是否存在可用的 refresh token。
 * Web 环境：本地不可读 HttpOnly Cookie，返回 true 表示「可能存在，应尝试刷新」，
 *           由后端 /auth/refresh 接口据 Cookie 实际校验结果决定后续。
 * Electron 环境：检查 safeStorage 中是否确实存在 refresh token。
 */
export async function hasRefreshToken(): Promise<boolean> {
  if (isWebEnvironment()) {
    return true
  }
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
