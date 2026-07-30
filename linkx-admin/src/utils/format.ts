import dayjs from 'dayjs'

export function formatTime(value?: string | number | Date | null) {
  if (value == null || value === '') return '-'
  const d = dayjs(value)
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm:ss') : '-'
}

/** 空值展示为「暂无」 */
export function displayOrNone(value?: string | null) {
  if (value == null || String(value).trim() === '') return '暂无'
  return value
}

/**
 * 将常见 IPv6 形式规范为 IPv4，便于管理端展示。
 * ::1 → 127.0.0.1，::ffff:x.x.x.x → x.x.x.x
 */
export function formatIp(ip?: string | null) {
  if (ip == null || String(ip).trim() === '') return '暂无'
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

/** 后端：1 正常，0 禁用（冻结/封禁） */
export function userStatusLabel(status?: number) {
  switch (status) {
    case 1:
      return '正常'
    case 0:
      return '禁用'
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
