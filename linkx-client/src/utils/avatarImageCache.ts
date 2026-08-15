/**
 * 头像图片内存缓存：避免消息状态更新导致 Avatar 重挂载后重复「占位 → 真图」闪烁。
 */
import { normalizeMediaUrl } from './mediaUrl'

const loadedUrls = new Set<string>()

export function isAvatarImageCached(url?: string | null): boolean {
  const normalized = normalizeMediaUrl(url)
  return !!normalized && loadedUrls.has(normalized)
}

export function markAvatarImageCached(url?: string | null): void {
  const normalized = normalizeMediaUrl(url)
  if (normalized) loadedUrls.add(normalized)
}

export function primeAvatarImageCache(url?: string | null): boolean {
  const normalized = normalizeMediaUrl(url)
  if (!normalized) return false
  if (loadedUrls.has(normalized)) return true
  if (typeof Image === 'undefined') return false
  const img = new Image()
  img.referrerPolicy = 'no-referrer'
  img.onload = () => {
    loadedUrls.add(normalized)
  }
  img.src = normalized
  if (img.complete) {
    loadedUrls.add(normalized)
    return true
  }
  return false
}
