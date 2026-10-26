/**
 * 作者：yangleduo
 */
import type { Plugin } from 'vite'
import type { DemoApp } from './types'

type PluginApp = DemoApp | 'portal'
import { ctxFromConfig } from './utils'
import { resolveAdminMock } from './admin/handlers'
import { resolveClientMock } from './client/handlers'
import { tryServeDemoMedia } from './shared/serve-media'

/** 开发态兜底：即使 axios 未 patch，/api 也不落到 Vite 404 */
async function resolvePayload(app: PluginApp, ctx: ReturnType<typeof ctxFromConfig>) {
  if (app === 'admin' || (app === 'portal' && ctx.path.startsWith('/admin'))) {
    return resolveAdminMock(ctx)
  }
  return resolveClientMock(ctx)
}

export function demoApiDevPlugin(app: PluginApp): Plugin {
  return {
    name: 'linkx-demo-api-dev',
    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        try {
        const url = req.url || ''
        const pathOnly = url.split('?')[0] || ''
        if (tryServeDemoMedia(pathOnly, res)) {
          return
        }
        const apiPath = url.startsWith('/client/api')
          ? url.slice('/client'.length)
          : url.startsWith('/admin/api')
            ? url.slice('/admin'.length)
            : url
        if (!apiPath.startsWith('/api')) {
          next()
          return
        }
        const apiPathOnly = apiPath.split('?')[0] || ''
        if (req.method && !['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].includes(req.method)) {
          next()
          return
        }
        const method = (req.method || 'GET').toLowerCase()
        let body: unknown
        if (method === 'post' || method === 'put' || method === 'patch') {
          body = await new Promise<unknown>((resolve, reject) => {
            const chunks: Buffer[] = []
            req.on('data', (chunk: Buffer) => chunks.push(chunk))
            req.on('end', () => {
              const raw = Buffer.concat(chunks).toString('utf8')
              if (!raw) {
                resolve(undefined)
                return
              }
              try {
                resolve(JSON.parse(raw))
              } catch {
                resolve(raw)
              }
            })
            req.on('error', reject)
          })
        }
        const query = apiPath.includes('?') ? apiPath.slice(apiPath.indexOf('?')) : ''
        try {
          const ctx = ctxFromConfig(
            {
              url: `${apiPathOnly.replace(/^\/api/, '') || '/'}${query}`,
              baseURL: '/api',
              method,
              data: body,
              params: {},
            } as import('axios').InternalAxiosRequestConfig,
            app
          )
          const payload = await resolvePayload(app, ctx)
          const data = payload ?? { code: 200, message: '', data: null }
          res.statusCode = 200
          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          res.end(JSON.stringify(data))
        } catch (err) {
          res.statusCode = 200
          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          res.end(JSON.stringify({ code: 200, message: '', data: null, _demoError: String(err) }))
        }
        } catch (err) {
          try {
            next()
          } catch {
            res.statusCode = 200
            res.setHeader('Content-Type', 'application/json; charset=utf-8')
            res.end(JSON.stringify({ code: 200, message: '', data: null, _demoError: String(err) }))
          }
        }
      })
    },
  }
}
