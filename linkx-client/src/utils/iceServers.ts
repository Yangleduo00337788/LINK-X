/**
 * WebRTC ICE 服务器解析：支持 VITE_ICE_SERVERS JSON，缺省使用公共 STUN。
 *
 * 示例：
 * VITE_ICE_SERVERS=[{"urls":"stun:stun.l.google.com:19302"},{"urls":"turn:turn.example.com:3478","username":"u","credential":"p"}]
 */

const DEFAULT_ICE_SERVERS: RTCIceServer[] = [
  { urls: 'stun:stun.l.google.com:19302' },
  { urls: 'stun:stun1.l.google.com:19302' }
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
