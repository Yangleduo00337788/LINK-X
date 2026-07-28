import { describe, it, expect, beforeEach, vi } from 'vitest'
import {
  purgeLegacyTokens,
  getToken,
  setToken,
  getRefreshToken,
  hasRefreshToken,
  saveTokenPair,
  clearTokens,
  isWebEnvironment
} from './tokenStorage'

describe('tokenStorage', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    // @ts-expect-error test stub
    delete window.electronAPI
  })

  it('Web 环境 token 由后端 HttpOnly Cookie 管理，本地读写为 no-op', async () => {
    expect(isWebEnvironment()).toBe(true)
    purgeLegacyTokens()
    // setTokenPair 在 Web 环境为空操作，不应写入任何本地存储
    await saveTokenPair('a', 'r')
    expect(sessionStorage.length).toBe(0)
    expect(localStorage.length).toBe(0)
    // getToken 在 Web 环境返回 null（token 在 HttpOnly Cookie 中，JS 不可读）
    expect(await getToken('accessToken')).toBeNull()
    expect(await getRefreshToken()).toBeNull()
    // hasRefreshToken 在 Web 环境返回 true（乐观假设 Cookie 可能存在，由后端校验）
    expect(await hasRefreshToken()).toBe(true)
    await setToken('accessToken', 'a2')
    expect(await getToken('accessToken')).toBeNull()
    // clearTokens 在 Web 环境为空操作（Cookie 由后端 logout 清除）
    await clearTokens()
    expect(await getToken('accessToken')).toBeNull()
  })

  it('Electron secureStorage 读写与清理', async () => {
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
    expect(isWebEnvironment()).toBe(false)
    await saveTokenPair('ea', 'er')
    expect(await getToken('accessToken')).toBe('ea')
    expect(await getRefreshToken()).toBe('er')
    expect(await hasRefreshToken()).toBe(true)
    await clearTokens()
    expect(await getToken('accessToken')).toBeNull()
  })
})
