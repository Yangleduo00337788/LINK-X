/**
 * 图片展示工具：有真实地址则直接展示，无地址才走占位。
 */
import { isDisplayableMediaUrl, normalizeMediaUrl } from './mediaUrl'

/** 可作为 <img src> 立即展示的地址（http/blob/data 等） */
export function pickDisplayableImageUrl(url?: string | null): string {
  const raw = (url || '').trim()
  if (raw.startsWith('blob:') || raw.startsWith('data:')) return raw
  const normalized = normalizeMediaUrl(url)
  if (normalized && isDisplayableMediaUrl(normalized)) return normalized
  return ''
}
