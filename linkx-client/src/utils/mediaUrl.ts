/**
 * 规范化媒体地址。
 *
 * Windows 上 localhost 常解析到 IPv6(::1)，Docker MinIO 只听 IPv4 会超时。
 * 但预签名 URL 的 Host 参与 AWS 签名，禁止改写，否则浏览器会 403、头像空白。
 * 正确做法：后端用 127.0.0.1 重新签发；前端仅对「未签名」的 localhost 做兼容改写。
 */

/** 是否可作为 <img src> 使用（已签发 http(s) / data / blob / 本地静态路径） */
export function isDisplayableMediaUrl(url?: string | null): boolean {
  if (!url) return false
  const v = url.trim()
  if (!v) return false
  // 历史占位：客户端并无该文件
  if (v === '/default-avatar.svg' || v.endsWith('/default-avatar.svg')) return false
  // MinIO object key（如 2026/07/xx.jpg）不能直接当图片地址
  if (!/^(https?:\/\/|data:|blob:|\/)/i.test(v)) return false
  return true
}

export function normalizeMediaUrl(url?: string | null): string {
  if (!url) return ''
  const trimmed = url.trim()
  if (!isDisplayableMediaUrl(trimmed)) return ''
  // 预签名：Host 不可改
  if (/[?&]X-Amz-/i.test(trimmed)) {
    return trimmed
  }
  return trimmed
    .replace(/\/\/localhost(?=:\d+|\/|$)/gi, '//127.0.0.1')
    .replace(/\/\/\[::1\](?=:\d+|\/|$)/gi, '//127.0.0.1')
}
