/**
 * 作者：yangleduo
 */

function pad2(n: number): string {
  return String(n).padStart(2, '0')
}

export function formatDateKey(date: Date): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

export function todayDateKey(refDate: Date = new Date()): string {
  return formatDateKey(refDate)
}

function addDays(base: Date, days: number): Date {
  const d = new Date(base)
  d.setDate(d.getDate() + days)
  d.setHours(0, 0, 0, 0)
  return d
}

function parseYmd(value: string): string | null {
  const match = value.match(/^(\d{4})-(\d{2})-(\d{2})$/)
  if (!match) return null
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const probe = new Date(year, month - 1, day)
  if (
    probe.getFullYear() !== year ||
    probe.getMonth() !== month - 1 ||
    probe.getDate() !== day
  ) {
    return null
  }
  return `${match[1]}-${match[2]}-${match[3]}`
}

const RELATIVE_DATE_RULES: Array<{ pattern: RegExp; offset: number }> = [
  { pattern: /^(今天|今日|today)$/i, offset: 0 },
  { pattern: /^(明天|明日|tomorrow)$/i, offset: 1 },
  { pattern: /^(后天|the day after tomorrow)$/i, offset: 2 },
  { pattern: /^(昨天|昨日|yesterday)$/i, offset: -1 }
]

/**
 * 将「明天」「2026-08-29」等解析为 YYYY-MM-DD。
 * 相对日期以客户端本地当天为基准。
 */
export function resolveEventDate(raw: unknown, refDate: Date = new Date()): string | null {
  const text = typeof raw === 'string' ? raw.trim() : ''
  if (!text) return null

  const ymd = parseYmd(text)
  if (ymd) return ymd

  const normalized = text.replace(/\s+/g, '')
  for (const rule of RELATIVE_DATE_RULES) {
    if (rule.pattern.test(normalized)) {
      return formatDateKey(addDays(refDate, rule.offset))
    }
  }

  for (const rule of RELATIVE_DATE_RULES) {
    if (rule.pattern.test(normalized.replace(/(上午|下午|晚上|中午|早上|傍晚).*$/u, ''))) {
      return formatDateKey(addDays(refDate, rule.offset))
    }
  }

  const embedded = text.match(/(\d{4}-\d{2}-\d{2})/)
  if (embedded) {
    return parseYmd(embedded[1])
  }

  return null
}

/** 下午/晚上等未给具体时间时的默认开始时间 */
export function inferDefaultStartTime(rawDate?: unknown): string {
  const text = typeof rawDate === 'string' ? rawDate : ''
  if (/下午|傍晚|晚上|night|afternoon|pm/i.test(text)) return '14:00'
  if (/中午|noon/i.test(text)) return '12:00'
  if (/早上|上午|morning|am/i.test(text)) return '09:00'
  return '14:00'
}

export function inferDefaultEndTime(startTime: string): string {
  const [hh, mm] = startTime.split(':').map(Number)
  if (Number.isNaN(hh) || Number.isNaN(mm)) return '15:00'
  const endH = Math.min(23, hh + 1)
  return `${pad2(endH)}:${pad2(mm)}`
}
