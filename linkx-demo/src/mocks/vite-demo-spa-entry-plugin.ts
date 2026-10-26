/**
 * 作者：yangleduo
 */
import type { IncomingMessage, ServerResponse } from 'node:http'
import type { Plugin } from 'vite'

/** 开发时把 SPA 入口重写到 demo.html，确保先安装 MOCK 再启动真实应用 */
export function demoSpaEntryPlugin(opts: { base: '/client/' | '/admin/' }): Plugin {
  const rewrite = (req: IncomingMessage, _res: ServerResponse, next: () => void) => {
    if (req.method !== 'GET' && req.method !== 'HEAD') {
      next()
      return
    }
    const raw = req.url || ''
    const [pathname, search = ''] = raw.split('?')
    const last = pathname.split('/').pop() || ''
    const looksLikeAsset = last.includes('.')
    const base = opts.base.replace(/\/$/, '')
    const isSpaEntry =
      pathname === '/' ||
      pathname === '/index.html' ||
      pathname === base ||
      pathname === `${base}/` ||
      pathname === `${base}/index.html`
    if (!isSpaEntry || looksLikeAsset) {
      next()
      return
    }
    req.url = `${base}/demo.html${search ? `?${search}` : ''}`
    next()
  }

  return {
    name: `linkx-demo-spa-entry-${opts.base.replace(/\//g, '')}`,
    configureServer(server) {
      server.middlewares.use(rewrite)
    },
    configurePreviewServer(server) {
      server.middlewares.use(rewrite)
    },
  }
}
