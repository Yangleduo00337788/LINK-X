/**
 * 作者：yangleduo
 * 下载演示用真实图片到 portal / client 静态目录（可重复执行）
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const demoRoot = fileURLToPath(new URL('..', import.meta.url))
const targets = [
  path.join(demoRoot, 'portal/public/demo-media'),
  path.join(demoRoot, '../linkx-client/public/demo-media'),
  path.join(demoRoot, '../linkx-admin/public/demo-media'),
]

const files = [
  { file: 'avatar-01.jpg', url: 'https://picsum.photos/seed/linkx-a01/256/256' },
  { file: 'avatar-02.jpg', url: 'https://picsum.photos/seed/linkx-a02/256/256' },
  { file: 'avatar-03.jpg', url: 'https://picsum.photos/seed/linkx-a03/256/256' },
  { file: 'avatar-04.jpg', url: 'https://picsum.photos/seed/linkx-a04/256/256' },
  { file: 'avatar-05.jpg', url: 'https://picsum.photos/seed/linkx-a05/256/256' },
  { file: 'avatar-06.jpg', url: 'https://picsum.photos/seed/linkx-a06/256/256' },
  { file: 'avatar-07.jpg', url: 'https://picsum.photos/seed/linkx-a07/256/256' },
  { file: 'avatar-08.jpg', url: 'https://picsum.photos/seed/linkx-a08/256/256' },
  { file: 'avatar-09.jpg', url: 'https://picsum.photos/seed/linkx-a09/256/256' },
  { file: 'avatar-10.jpg', url: 'https://picsum.photos/seed/linkx-a10/256/256' },
  { file: 'avatar-11.jpg', url: 'https://picsum.photos/seed/linkx-a11/256/256' },
  { file: 'avatar-12.jpg', url: 'https://picsum.photos/seed/linkx-a12/256/256' },
  { file: 'avatar-admin.jpg', url: 'https://picsum.photos/seed/linkx-admin/256/256' },
  { file: 'chat-photo-01.jpg', url: 'https://picsum.photos/seed/linkx-chat1/960/640' },
  { file: 'chat-photo-02.jpg', url: 'https://picsum.photos/seed/linkx-chat2/960/640' },
  { file: 'moments-01.jpg', url: 'https://picsum.photos/seed/linkx-moments1/1080/720' },
  { file: 'moments-02.jpg', url: 'https://picsum.photos/seed/linkx-moments2/1080/720' },
  { file: 'banner-ops.jpg', url: 'https://picsum.photos/seed/linkx-banner/1440/360' },
  { file: 'short-video-cover.jpg', url: 'https://picsum.photos/seed/linkx-sv/720/1280' },
  { file: 'group-cover.jpg', url: 'https://picsum.photos/seed/linkx-group/800/400' },
]

async function download(url) {
  const res = await fetch(url, { redirect: 'follow' })
  if (!res.ok) throw new Error(`HTTP ${res.status} ${url}`)
  return Buffer.from(await res.arrayBuffer())
}

for (const dir of targets) {
  fs.mkdirSync(dir, { recursive: true })
  for (const { file, url } of files) {
    const dest = path.join(dir, file)
    try {
      const buf = await download(url)
      fs.writeFileSync(dest, buf)
      console.log('[demo-media]', path.relative(demoRoot, dest))
    } catch (e) {
      console.warn('[demo-media] skip', file, e instanceof Error ? e.message : e)
    }
  }
}

console.log('[demo-media] done')
