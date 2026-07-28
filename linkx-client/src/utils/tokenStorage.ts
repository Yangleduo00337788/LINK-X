/**
 * @security Web 环境 Token 存储安全说明
 *
 * 已知风险（已评估，可控）：
 * - Web 浏览器环境下，JWT 使用 sessionStorage 存储，存在 XSS 风险。
 *   一旦渲染进程被 XSS 注入，攻击者可读取 sessionStorage 中的 token。
 * - 彻底修复需后端配合改为 HttpOnly + Secure + SameSite Cookie，
 *   但当前架构为 Electron 桌面客户端优先，Web 仅作降级方案，改动过大。
 *
 * 当前缓解措施：
 * 1. Electron 桌面环境（主场景）使用 safeStorage 加密落盘（keychain/Credential Vault），不存明文。
 * 2. Web 环境使用 sessionStorage 而非 localStorage，关闭标签即清，避免长期明文驻留。
 * 3. 渲染进程启用严格 CSP（见 electron/main.ts），限制脚本来源，降低 XSS 概率。
 * 4. 启动时调用 purgeLegacyTokens() 清理历史 localStorage 残留。
 *
 * 后续演进：Web 场景若正式商用，应迁移至 HttpOnly Cookie + 后端刷新令牌轮换。
 */
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
