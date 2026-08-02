/**
 * 全 API 压测安全护栏（k6 / Node 共用）
 * - 路径占位用高位哨兵 ID，降低误伤真实资源
 * - 黑名单跳过会清账号/清数据的写操作
 */

export const SENTINEL_ID = '900000000000000001'

/** @returns {string} */
export function fillPath(p) {
  return String(p)
    .replace(/\{[^}]*[Ii]d\}/g, SENTINEL_ID)
    .replace(/\{conversationId\}/g, SENTINEL_ID)
    .replace(/\{[^}]+\}/g, SENTINEL_ID)
}

/**
 * 压测永远跳过的写操作（method + 规范化 path）
 * path 使用 OpenAPI 模板形式，如 /admin/users/{id}/ban
 */
const DENY_EXACT = new Set([
  'POST /compliance/purge',
  'POST /user/delete-account',
  'POST /auth/logout',
  'POST /admin/auth/logout',
  'POST /auth/reset-password',
  'POST /auth/reset-password-by-email',
  'POST /auth/reset-password-captcha',
  'POST /admin/users/{id}/reset-password',
  'POST /admin/users/{id}/ban',
  'POST /admin/users/{id}/unban',
  'POST /admin/users/{id}/devices/{deviceId}/revoke',
  'POST /admin/devices/{userId}/{deviceId}/kick',
  'POST /admin/devices/{userId}/{deviceId}/ban',
  'POST /admin/devices/{userId}/{deviceId}/unban',
  'DELETE /notifications/clear',
])

/** @param {{ method?: string, path?: string }} ep */
export function isMutatingDenied(ep) {
  const method = String(ep.method || '').toUpperCase()
  const path = String(ep.path || '')
  if (DENY_EXACT.has(`${method} ${path}`)) return true
  // reset-password / logout 系列兜底（含未来变体）
  if (method === 'POST' && (path.includes('reset-password') || path.endsWith('/logout'))) return true
  return false
}

/** 过滤 SSE / 黑名单 */
export function filterCatalog(list, { includeMutating }) {
  let out = list || []
  if (!includeMutating) out = out.filter((e) => !e.mutating)
  out = out.filter((e) => !String(e.path || '').includes('/events/stream'))
  out = out.filter((e) => !isMutatingDenied(e))
  return out
}
