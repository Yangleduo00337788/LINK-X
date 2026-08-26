/**
 * 作者：yangleduo
 */
import type { ShortVideoPost } from '../api/shortVideo'
import { buildShortVideoMediaApiUrl } from './shortVideoMediaAccess'

/** 资料卡等场景：取最近若干条作品的封面鉴权 URL。 */
export function collectShortVideoCoverPreviews(posts: ShortVideoPost[], limit = 4): string[] {
  const covers: string[] = []
  for (const post of posts) {
    const id = String(post.id || '').trim()
    if (!id) continue
    covers.push(buildShortVideoMediaApiUrl(id, 'cover'))
    if (covers.length >= limit) break
  }
  return covers
}
