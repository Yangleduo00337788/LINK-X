/**
 * 作者：yangleduo
 */
import type { IncomingMessage, ServerResponse } from 'node:http'
import type { Plugin } from 'vite'

/** 开发/预览把 SPA 入口改写到 demo.html，确保 axios + WS MOCK 先于真实 main 安装 */
export function demoSpaEntryPlugin(opts: { base: '/client' | '/admin' }): Plugin {
  const prefix = opts.base
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
      pathname === prefix ||
      pathname === `${prefix}/` ||
      pathname === `${prefix}/index.html` ||
      (!looksLikeAsset && pathname === `${prefix}/`)
    if (!isSpaEntry) {
      next()
      return
    }
    req.url = `${prefix}/demo.html${search ? `?${search}` : ''}`
    next()
  }

  return {
    name: `linkx-demo-${prefix.slice(1)}-entry`,
    enforce: 'pre',
    configureServer(server) {
      server.middlewares.use(rewrite)
    },
    configurePreviewServer(server) {
      server.middlewares.use(rewrite)
    },
  }
}
