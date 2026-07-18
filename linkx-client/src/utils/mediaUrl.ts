/**
 * 规范化媒体地址。
 *
 * Windows 上 localhost 常解析到 IPv6(::1)，Docker MinIO 只听 IPv4 会超时。
 * 但预签名 URL 的 Host 参与 AWS 签名，禁止改写，否则浏览器会 403、头像空白。
 * 正确做法：后端用 127.0.0.1 重新签发；前端仅对「未签名」的 localhost 做兼容改写。
 */
export function normalizeMediaUrl(url?: string | null): string {
  if (!url) return ''
  // 预签名：Host 不可改
  if (/[?&]X-Amz-/i.test(url)) {
    return url
  }
  return url
    .replace(/\/\/localhost(?=:\d+|\/|$)/gi, '//127.0.0.1')
    .replace(/\/\/\[::1\](?=:\d+|\/|$)/gi, '//127.0.0.1')
}
