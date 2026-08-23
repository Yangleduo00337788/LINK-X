/**
 * 作者：yangleduo
 */
/**
 * 短视频鉴权媒体访问（Electron 走 blob，Web 走同源 Cookie）。
 */
import { fetchAuthenticatedApiBlobUrl } from './authDownload'
import { normalizeMediaUrl } from './mediaUrl'
import { isWebEnvironment } from './tokenStorage'
import { API_BASE_URL } from '../config/endpoints'

export function buildShortVideoMediaApiUrl(postId: string, kind: 'video' | 'cover' = 'video'): string {
  const path =
    kind === 'video'
      ? `/short-video/${encodeURIComponent(postId)}/video/content`
      : `/short-video/${encodeURIComponent(postId)}/cover/content`
  return `${API_BASE_URL}${path}`
}

export async function fetchShortVideoMediaBlobUrl(
  postId: string,
  kind: 'video' | 'cover' = 'video'
): Promise<string | null> {
  const path =
    kind === 'video'
      ? `/short-video/${postId}/video/content`
      : `/short-video/${postId}/cover/content`
  return fetchAuthenticatedApiBlobUrl(path)
}

export async function resolveShortVideoDisplaySrc(
  postId: string,
  kind: 'video' | 'cover' = 'video',
  fallbackUrl?: string
): Promise<{ src: string; blobUrlToRevoke?: string }> {
  const id = postId?.trim()
  if (id) {
    if (isWebEnvironment()) {
      return { src: buildShortVideoMediaApiUrl(id, kind) }
    }
    const blobUrl = await fetchShortVideoMediaBlobUrl(id, kind)
    if (blobUrl) {
      return { src: blobUrl, blobUrlToRevoke: blobUrl }
    }
  }
  const fallback = (fallbackUrl || '').trim()
  if (fallback.startsWith('blob:') || fallback.startsWith('data:')) {
    return { src: fallback }
  }
  return { src: normalizeMediaUrl(fallback) || fallback }
}
