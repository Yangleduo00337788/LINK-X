/**
 * 作者：yangleduo
 */
import { API_BASE_URL } from '../config/endpoints'

/**
 * 将版本检查接口返回的下载地址解析为绝对 URL（供 Electron 下载安装包）。
 */
export function resolveAppDownloadUrl(url: string): string {
  const trimmed = (url || '').trim()
  if (!trimmed) return ''
  if (/^https?:\/\//i.test(trimmed)) return trimmed
  if (trimmed.startsWith('/')) {
    const base = API_BASE_URL.replace(/\/$/, '')
    return `${base}${trimmed}`
  }
  return trimmed
}
