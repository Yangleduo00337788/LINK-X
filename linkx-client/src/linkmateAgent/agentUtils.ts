/**
 * 作者：yangleduo
 */

export function asString(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

export function asStringArray(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.map(item => asString(item)).filter(Boolean)
  }
  const raw = asString(value)
  if (!raw) return []
  return raw
    .split(/[,，、]/)
    .map(item => item.trim())
    .filter(Boolean)
}

export function truncate(text: string, max = 40): string {
  if (text.length <= max) return text
  return `${text.slice(0, max)}…`
}

export function parseActionEnum<T extends string>(
  value: unknown,
  allowed: readonly T[]
): T | null {
  const raw = asString(value).toLowerCase()
  if (!raw) return null
  return (allowed as readonly string[]).includes(raw) ? (raw as T) : null
}
