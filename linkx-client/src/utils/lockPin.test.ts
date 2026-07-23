import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  saveLockPinHash,
  hasLockPin,
  verifyLockPin,
  clearLockPin
} from './lockPin'

describe('lockPin', () => {
  beforeEach(() => {
    localStorage.clear()
    const store = new Map<string, string>()
    // @ts-expect-error stub
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
  })

  it('保存并校验 PIN', async () => {
    expect(hasLockPin()).toBe(false)
    await saveLockPinHash('1234')
    expect(hasLockPin()).toBe(true)
    expect(await verifyLockPin('1234')).toBe(true)
    expect(await verifyLockPin('9999')).toBe(false)
    await clearLockPin()
    expect(hasLockPin()).toBe(false)
    expect(await verifyLockPin('1234')).toBe(false)
  })
})
