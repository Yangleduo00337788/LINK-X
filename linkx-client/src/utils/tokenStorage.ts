const ACCESS_KEY = 'accessToken'
const REFRESH_KEY = 'refreshToken'
const FALLBACK_PREFIX = 'linkx:token:'

type TokenKey = typeof ACCESS_KEY | typeof REFRESH_KEY

function fallbackKey(key: TokenKey): string {
  return FALLBACK_PREFIX + key
}

async function secureGet(key: string): Promise<string | null> {
  const api = window.electronAPI?.secureStorage
  if (api) {
    const available = await api.isAvailable()
    if (available) {
      return api.get(key)
    }
  }
  return localStorage.getItem(fallbackKey(key as TokenKey))
}

async function secureSet(key: string, value: string): Promise<void> {
  const api = window.electronAPI?.secureStorage
  if (api) {
    const available = await api.isAvailable()
    if (available) {
      await api.set(key, value)
      localStorage.removeItem(fallbackKey(key as TokenKey))
      return
    }
  }
  localStorage.setItem(fallbackKey(key as TokenKey), value)
}

async function secureRemove(key: string): Promise<void> {
  const api = window.electronAPI?.secureStorage
  if (api) {
    await api.remove(key)
  }
  localStorage.removeItem(fallbackKey(key as TokenKey))
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
