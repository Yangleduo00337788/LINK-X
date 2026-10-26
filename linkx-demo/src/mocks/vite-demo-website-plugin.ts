/**
 * 作者：yangleduo
 * 把仓库内官网静态页接到演示站：
 * - /help 对齐官网帮助中心
 * - /site 挂整站（协议、隐私、文档等，避免和门户首页抢 /）
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import type { Plugin } from 'vite'

const demoRoot = fileURLToPath(new URL('../..', import.meta.url))
const websiteRoot = path.join(demoRoot, 'website')

const MIME: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.json': 'application/json; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
}

function resolveWebsiteFile(urlPath: string): string | null {
  let rel = ''
  if (urlPath === '/help' || urlPath.startsWith('/help/')) {
    rel = urlPath === '/help' || urlPath === '/help/' ? '/help/index.html' : urlPath
  } else if (urlPath === '/site' || urlPath.startsWith('/site/')) {
    rel = urlPath.slice('/site'.length) || '/'
    if (!rel || rel === '/') rel = '/index.html'
  } else {
    return null
  }
  rel = decodeURIComponent(rel)
  if (rel.endsWith('/')) rel += 'index.html'
  const full = path.normalize(path.join(websiteRoot, rel))
  if (!full.startsWith(websiteRoot)) return null
  if (fs.existsSync(full) && fs.statSync(full).isFile()) return full
  if (fs.existsSync(full) && fs.statSync(full).isDirectory()) {
    const index = path.join(full, 'index.html')
    if (fs.existsSync(index)) return index
  }
  return null
}

export function demoWebsitePlugin(): Plugin {
  return {
    name: 'linkx-demo-website',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const url = (req.url || '').split('?')[0] || ''
        const file = resolveWebsiteFile(url)
        if (!file) {
          next()
          return
        }
        const ext = path.extname(file).toLowerCase()
        res.statusCode = 200
        res.setHeader('Content-Type', MIME[ext] || 'application/octet-stream')
        fs.createReadStream(file).pipe(res)
      })
    },
  }
}
