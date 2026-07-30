import dayjs from 'dayjs'
import { tGlobal } from '@/i18n'

export function formatTime(value?: string | number | Date | null) {
  if (value == null || value === '') return '-'
  const d = dayjs(value)
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm:ss') : '-'
}

/** Empty values show as localized “N/A” */
export function displayOrNone(value?: string | null) {
  if (value == null || String(value).trim() === '') return tGlobal('common.none')
  return value
}

/**
 * Normalize common IPv6 forms to IPv4 for admin display.
 * ::1 → 127.0.0.1，::ffff:x.x.x.x → x.x.x.x
 */
export function formatIp(ip?: string | null) {
  if (ip == null || String(ip).trim() === '') return tGlobal('common.none')
  let v = String(ip).trim()
  if (v.startsWith('[') && v.includes(']')) {
    v = v.slice(1, v.indexOf(']'))
  } else if ((v.match(/\./g) || []).length === 3 && v.includes(':')) {
    v = v.slice(0, v.lastIndexOf(':'))
  }
  const lower = v.toLowerCase()
  if (lower === '::1' || lower === '0:0:0:0:0:0:0:1') {
    return '127.0.0.1'
  }
  if (lower.startsWith('::ffff:')) {
    const mapped = v.slice('::ffff:'.length)
    if (mapped.includes('.')) return mapped
    const hexs = mapped.split(':')
    if (hexs.length === 2) {
      const hi = Number.parseInt(hexs[0], 16)
      const lo = Number.parseInt(hexs[1], 16)
      if (!Number.isNaN(hi) && !Number.isNaN(lo)) {
        return `${(hi >> 8) & 0xff}.${hi & 0xff}.${(lo >> 8) & 0xff}.${lo & 0xff}`
      }
    }
  }
  return v
}

/** Backend: 1 active, 0 disabled (frozen/banned) */
export function userStatusLabel(status?: number) {
  switch (status) {
    case 1:
      return tGlobal('common.normal')
    case 0:
      return tGlobal('common.frozen')
    default:
      return status == null ? '-' : String(status)
  }
}

export function userStatusType(status?: number): 'success' | 'error' | 'default' {
  switch (status) {
    case 1:
      return 'success'
    case 0:
      return 'error'
    default:
      return 'default'
  }
}
