/**
 * 作者：yangleduo
 */
/**
 * WebRTC ICE 服务器解析：支持 VITE_ICE_SERVERS JSON，缺省使用公共 STUN（IP，避开 Electron DNS -105）。
 *
 * 同局域网仅靠 host candidate 即可连通；STUN 失败/超时通常可忽略。
 * 对称 NAT / 公司网仍建议配置 TURN：
 * VITE_ICE_SERVERS=[{"urls":"stun:162.159.207.0:3478"},{"urls":"turn:turn.example.com:3478","username":"u","credential":"p"}]
 */

const DEFAULT_ICE_SERVERS: RTCIceServer[] = [
  // Cloudflare / 小米 STUN：写 IP，避免 Electron 内 Chromium DNS 解析失败
  { urls: 'stun:162.159.207.0:3478' },
  { urls: 'stun:111.206.174.2:3478' }
]

export function resolveIceServers(): RTCIceServer[] {
  const raw = import.meta.env.VITE_ICE_SERVERS
  if (!raw || typeof raw !== 'string' || !raw.trim()) {
    return DEFAULT_ICE_SERVERS
  }
  try {
    const parsed = JSON.parse(raw) as unknown
    if (!Array.isArray(parsed) || parsed.length === 0) {
      return DEFAULT_ICE_SERVERS
    }
    const servers: RTCIceServer[] = []
    for (const item of parsed) {
      if (!item || typeof item !== 'object') continue
      const entry = item as Record<string, unknown>
      const urls = entry.urls
      if (typeof urls !== 'string' && !Array.isArray(urls)) continue
      const server: RTCIceServer = {
        urls: urls as string | string[]
      }
      if (typeof entry.username === 'string') server.username = entry.username
      if (typeof entry.credential === 'string') server.credential = entry.credential
      servers.push(server)
    }
    return servers.length > 0 ? servers : DEFAULT_ICE_SERVERS
  } catch (e) {
    console.warn('[iceServers] VITE_ICE_SERVERS 解析失败，使用默认 STUN', e)
    return DEFAULT_ICE_SERVERS
  }
}
