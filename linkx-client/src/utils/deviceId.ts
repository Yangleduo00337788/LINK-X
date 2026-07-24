/** 持久化本机设备 ID，供踢下线与多端同步。 */
const STORAGE_KEY = 'linkx_device_id'

export function getOrCreateDeviceId(): string {
  try {
    const existing = localStorage.getItem(STORAGE_KEY)
    if (existing && existing.trim()) return existing.trim()
    const id =
      typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `web-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
    localStorage.setItem(STORAGE_KEY, id)
    return id
  } catch {
    return 'default-web-device'
  }
}

export function getDeviceName(): string {
  if (typeof navigator === 'undefined') return 'Web'
  const ua = navigator.userAgent || ''
  if (/Electron/i.test(ua)) return 'LinkX Desktop'
  if (/Mobile|Android|iPhone/i.test(ua)) return 'Mobile Browser'
  return 'Web Browser'
}

export function getDeviceType(): string {
  if (typeof navigator === 'undefined') return 'Web'
  const ua = navigator.userAgent || ''
  if (/Electron/i.test(ua)) return 'Desktop'
  if (/Mobile|Android|iPhone/i.test(ua)) return 'Mobile'
  return 'Web'
}
