/**
 * 作者：yangleduo
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import type { ServerResponse } from 'node:http'

const demoRoot = fileURLToPath(new URL('../../..', import.meta.url))
const mediaRoots = [
  path.join(demoRoot, 'portal/public/demo-media'),
  path.join(demoRoot, '../linkx-client/public/demo-media'),
  path.join(demoRoot, '../linkx-admin/public/demo-media'),
]

function resolveMediaFile(relative: string): string | null {
  for (const root of mediaRoots) {
    const full = path.join(root, relative)
    if (fs.existsSync(full)) return full
  }
  return null
}

function pickAvatarFile(userId: string): string {
  const n = Number.parseInt(userId, 10)
  const idx = Number.isFinite(n) ? n % 12 : 0
  return `avatar-${String((idx || 0) + 1).padStart(2, '0')}.jpg`
}

function pickChatFile(messageId: string): string {
  return messageId.endsWith('4') || messageId.includes('104') ? 'chat-photo-02.jpg' : 'chat-photo-01.jpg'
}

function stripAppPrefix(urlPath: string): string {
  let s = urlPath.split('?')[0] || ''
  if (s.startsWith('/client')) s = s.slice('/client'.length) || '/'
  if (s.startsWith('/admin')) s = s.slice('/admin'.length) || '/'
  if (s.startsWith('/api')) s = s.slice('/api'.length) || '/'
  return s
}

function pipeJpeg(file: string, res: ServerResponse): boolean {
  res.statusCode = 200
  res.setHeader('Content-Type', 'image/jpeg')
  res.setHeader('Cache-Control', 'public, max-age=86400')
  fs.createReadStream(file).pipe(res)
  return true
}

export function tryServeDemoMedia(apiPath: string, res: ServerResponse): boolean {
  const p = stripAppPrefix(apiPath)

  let m = p.match(/^\/media\/avatars\/([^/?]+)/)
  if (m) {
    const file = resolveMediaFile(pickAvatarFile(m[1]))
    if (file) return pipeJpeg(file, res)
  }

  m = p.match(/^\/media\/chat\/([^/?]+)/)
  if (m) {
    const file = resolveMediaFile(m[1])
    if (file) return pipeJpeg(file, res)
  }

  m = p.match(/^\/media\/ops\/([^/?]+)/)
  if (m) {
    const file = resolveMediaFile(m[1])
    if (file) return pipeJpeg(file, res)
  }

  m = p.match(/^\/media\/moments-background\/([^/?]+)/)
  if (m) {
    const file = resolveMediaFile('moments-01.jpg')
    if (file) return pipeJpeg(file, res)
  }

  m = p.match(/^\/chat\/messages\/([^/]+)\/file$/)
  if (m) {
    const file = resolveMediaFile(pickChatFile(m[1]))
    if (file) return pipeJpeg(file, res)
  }

  m = p.match(/^\/cloud\/files\/([^/]+)\/content$/)
  if (m) {
    const file = resolveMediaFile('chat-photo-01.jpg')
    if (file) return pipeJpeg(file, res)
  }

  if (p.startsWith('/media/')) {
    const name = decodeURIComponent(p.split('/').pop() || '')
    const file = resolveMediaFile(name) || resolveMediaFile('chat-photo-01.jpg')
    if (file) return pipeJpeg(file, res)
  }

  return false
}

export function isDemoMediaRequest(url: string): boolean {
  const p = stripAppPrefix(url)
  return (
    /^\/media\//.test(p) ||
    /^\/chat\/messages\/[^/]+\/file$/.test(p) ||
    /^\/cloud\/files\/[^/]+\/content$/.test(p)
  )
}
