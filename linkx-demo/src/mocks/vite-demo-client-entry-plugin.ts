/**
 * 作者：yangleduo
 */
import type { IncomingMessage, ServerResponse } from 'node:http'
import type { Plugin } from 'vite'

/** 开发/预览时让 /client/ 走 demo.html（安装 axios + WebSocket MOCK），避免只吃到 portal 的 /api 兜底 */
export function demoClientEntryPlugin(): Plugin {
  const rewrite = (req: IncomingMessage, _res: ServerResponse, next: () => void) => {
    if (req.method !== 'GET' && req.method !== 'HEAD') {
      next()
      return
    }
    const raw = req.url || ''
    const [pathname, search = ''] = raw.split('?')
    const last = pathname.split('/').pop() || ''
    const looksLikeAsset = last.includes('.')
    const isSpaEntry =
      pathname === '/' ||
      pathname === '/index.html' ||
      pathname === '/client' ||
      pathname === '/client/' ||
      pathname === '/client/index.html' ||
      (!looksLikeAsset && /^\/client\/?$/.test(pathname))
    if (!isSpaEntry) {
      next()
      return
    }
    const demoPath = '/client/demo.html'
    req.url = `${demoPath}${search ? `?${search}` : ''}`
    next()
  }

  return {
    name: 'linkx-demo-client-entry',
    configureServer(server) {
      server.middlewares.use(rewrite)
    },
    configurePreviewServer(server) {
      server.middlewares.use(rewrite)
    },
  }
}
