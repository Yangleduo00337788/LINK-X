/**
 * 作者：yangleduo
 */
const STORAGE_KEY = 'lx:short-video-search-history'
const MAX_ITEMS = 10

function readRaw(): string[] {
  if (typeof localStorage === 'undefined') return []
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
  } catch {
    return []
  }
}

function writeRaw(items: string[]) {
  if (typeof localStorage === 'undefined') return
  localStorage.setItem(STORAGE_KEY, JSON.stringify(items))
}

export function loadShortVideoSearchHistory(): string[] {
  return readRaw()
}

export function saveShortVideoSearchQuery(query: string) {
  const q = query.trim()
  if (!q) return
  const next = [q, ...readRaw().filter(item => item !== q)].slice(0, MAX_ITEMS)
  writeRaw(next)
}

export function removeShortVideoSearchHistoryItem(query: string) {
  writeRaw(readRaw().filter(item => item !== query))
}

export function clearShortVideoSearchHistory() {
  writeRaw([])
}
