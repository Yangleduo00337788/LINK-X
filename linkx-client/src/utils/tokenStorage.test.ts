import { describe, it, expect, beforeEach, vi } from 'vitest'
import {
  purgeLegacyTokens,
  getToken,
  setToken,
  getRefreshToken,
  hasRefreshToken,
  saveTokenPair,
  clearTokens
} from './tokenStorage'

describe('tokenStorage', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    // @ts-expect-error test stub
    delete window.electronAPI
  })

  it('web sessionStorage 读写与清理', async () => {
    purgeLegacyTokens()
    await saveTokenPair('a', 'r')
    expect(await getToken('accessToken')).toBe('a')
    expect(await getRefreshToken()).toBe('r')
    expect(await hasRefreshToken()).toBe(true)
    await setToken('accessToken', 'a2')
    expect(await getToken('accessToken')).toBe('a2')
    await clearTokens()
    expect(await getToken('accessToken')).toBeNull()
    expect(await hasRefreshToken()).toBe(false)
  })

  it('Electron secureStorage 优先', async () => {
    const store = new Map<string, string>()
    // @ts-expect-error test stub
    window.electronAPI = {
      secureStorage: {
        isAvailable: vi.fn(async () => true),
        get: vi.fn(async (k: string) => store.get(k) ?? null),
        set: vi.fn(async (k: string, v: string) => {
          store.set(k, v)
        }),
        remove: vi.fn(async (k: string) => {
          store.delete(k)
        })
      }
    }
    await saveTokenPair('ea', 'er')
    expect(await getToken('accessToken')).toBe('ea')
    await clearTokens()
    expect(await getToken('accessToken')).toBeNull()
  })
})
