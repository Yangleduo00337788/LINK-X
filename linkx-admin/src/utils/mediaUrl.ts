/**
 * 管理端头像地址解析。
 *
 * 与客户端相同结论：浏览器直连 MinIO 预签名（:9000 / X-Amz-*）经常失败或空白；
 * 有 userId 时一律走后端同源代理 /api/media/avatars/{id}。
 */

const API_BASE = () => (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

function isExternalCdn(url: string): boolean {
  if (!/^https?:\/\//i.test(url)) return false
  if (/[?&]X-Amz-/i.test(url)) return false
  if (/:\/\/(127\.0\.0\.1|localhost|\[::1\])(:\d+)?\//i.test(url)) return false
  return true
}

/**
 * @param forceProxy 个人中心/顶栏：即使本地缓存丢了 avatar 字段也尝试同源代理
 */
export function resolveAvatarSrc(
  url?: string | null,
  userId?: number | null,
  forceProxy = false
): string {
  const v = (url || '').trim()
  if (v.startsWith('data:') || v.startsWith('blob:')) return v

  if (isExternalCdn(v)) return v

  if (userId != null && userId > 0) {
    if (!v && !forceProxy) return ''
    const base = API_BASE()
    if (v.startsWith('/media/')) return `${base}${v}`
    return `${base}/media/avatars/${userId}`
  }

  if (v.startsWith('/media/')) return `${API_BASE()}${v}`
  if (/^https?:\/\//i.test(v)) return v
  return ''
}

/** Banner 展示地址：同源 /media/banners/{id} 或外链 / blob */
export function resolveBannerSrc(url?: string | null): string {
  return resolveOpsMediaSrc(url)
}

/** 推荐位展示地址：同源 /media/recommends/{id} 或外链 / blob */
export function resolveRecommendSrc(url?: string | null): string {
  return resolveOpsMediaSrc(url)
}

/** 活动封面展示地址：同源 /media/activities/{id} 或外链 / blob */
export function resolveActivitySrc(url?: string | null): string {
  return resolveOpsMediaSrc(url)
}

function resolveOpsMediaSrc(url?: string | null): string {
  const v = (url || '').trim()
  if (!v) return ''
  if (v.startsWith('data:') || v.startsWith('blob:')) return v
  if (isExternalCdn(v)) return v
  if (v.startsWith('/media/')) return `${API_BASE()}${v}`
  if (/^https?:\/\//i.test(v)) return v
  return ''
}
