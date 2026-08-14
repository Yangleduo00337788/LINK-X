/**
 * 作者：yangleduo
 */
/** 对 Storage.setItem 做防抖，避免高频状态变更（如流式消息）阻塞主线程 */
export interface DebouncedStorage extends Storage {
  flushAll(): void
}

export function createDebouncedStorage(backing: Storage, debounceMs: number): DebouncedStorage {
  const timers = new Map<string, ReturnType<typeof setTimeout>>()
  const pending = new Map<string, string | null>()

  const flushKey = (key: string) => {
    const timer = timers.get(key)
    if (timer) {
      clearTimeout(timer)
      timers.delete(key)
    }
    if (!pending.has(key)) return
    const value = pending.get(key)
    pending.delete(key)
    if (value === null) {
      backing.removeItem(key)
    } else if (value != null) {
      backing.setItem(key, value)
    }
  }

  const flushAll = () => {
    for (const key of [...pending.keys()]) {
      flushKey(key)
    }
  }

  return {
    get length() {
      return backing.length
    },
    key(index: number) {
      return backing.key(index)
    },
    getItem(key: string) {
      if (pending.has(key)) {
        const value = pending.get(key)
        return value ?? null
      }
      return backing.getItem(key)
    },
    setItem(key: string, value: string) {
      pending.set(key, value)
      const existing = timers.get(key)
      if (existing) clearTimeout(existing)
      timers.set(
        key,
        setTimeout(() => flushKey(key), debounceMs)
      )
    },
    removeItem(key: string) {
      pending.set(key, null)
      const existing = timers.get(key)
      if (existing) clearTimeout(existing)
      timers.set(
        key,
        setTimeout(() => flushKey(key), debounceMs)
      )
    },
    clear() {
      for (const timer of timers.values()) clearTimeout(timer)
      timers.clear()
      pending.clear()
      backing.clear()
    },
    flushAll
  }
}

/** 聊天记录 sessionStorage 写入防抖（2s），流式/高频消息时避免同步 JSON 落盘卡顿 */
export const debouncedSessionStorage = createDebouncedStorage(sessionStorage, 2000)
