/**
 * 作者：yangleduo
 */
/** 管理端设备标识，供风控与设备绑定。 */
const STORAGE_KEY = 'linkx_admin_device_id'

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
    return typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `web-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
  }
}

export function getDeviceName(): string {
  if (typeof navigator === 'undefined') return 'Admin Web'
  const ua = navigator.userAgent || ''
  if (/Electron/i.test(ua)) return 'LinkX Admin Desktop'
  if (/Mobile|Android|iPhone/i.test(ua)) return 'Admin Mobile Browser'
  return 'Admin Web Browser'
}

export function getDeviceType(): string {
  if (typeof navigator === 'undefined') return 'Web'
  const ua = navigator.userAgent || ''
  if (/Electron/i.test(ua)) return 'Desktop'
  if (/Mobile|Android|iPhone/i.test(ua)) return 'Mobile'
  return 'Web'
}

export function getDeviceHeaders(): Record<string, string> {
  return {
    'X-Device-Id': getOrCreateDeviceId(),
    'X-Device-Name': getDeviceName(),
    'X-Device-Type': getDeviceType(),
  }
}
