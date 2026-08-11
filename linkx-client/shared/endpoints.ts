/**
 * 作者：yangleduo
 */
/**
 * API / WS 基址与 CSP connect-src 解析（主进程与渲染进程共用逻辑）。
 */
const trimSlash = (url: string) => url.replace(/\/$/, '')

export const DEFAULT_API_BASE_URL = 'http://127.0.0.1:8080/api'
export const DEFAULT_WS_BASE_URL = 'ws://127.0.0.1:8081'

export function resolveApiBaseUrl(envApi?: string): string {
  return trimSlash(envApi || DEFAULT_API_BASE_URL)
}

export function resolveWsBaseUrl(envWs?: string): string {
  return trimSlash(envWs || DEFAULT_WS_BASE_URL)
}

/** 从 API/WS 基址提取 origin（含协议与端口），供 CSP connect-src 使用 */
export function originFromBaseUrl(baseUrl: string): string {
  try {
    const u = new URL(baseUrl)
    return `${u.protocol}//${u.host}`
  } catch {
    return baseUrl
  }
}

/**
 * 本机回环地址在 CSP 中视为不同源：localhost 与 127.0.0.1 需同时放行，
 * 否则渲染进程连 127.0.0.1 时会被主进程 CSP 拦截。
 */
export function expandLoopbackOrigins(origin: string): string[] {
  const out = new Set<string>([origin])
  try {
    const u = new URL(origin)
    const port = u.port ? `:${u.port}` : ''
    if (u.hostname === 'localhost') {
      out.add(`${u.protocol}//127.0.0.1${port}`)
    } else if (u.hostname === '127.0.0.1') {
      out.add(`${u.protocol}//localhost${port}`)
    }
  } catch {
    /* ignore */
  }
  return [...out]
}

export function connectOriginsForCsp(apiBase: string, wsBase: string): string {
  const origins = new Set<string>()
  for (const origin of [originFromBaseUrl(apiBase), originFromBaseUrl(wsBase)]) {
    for (const expanded of expandLoopbackOrigins(origin)) {
      origins.add(expanded)
    }
  }
  return [...origins].join(' ')
}
