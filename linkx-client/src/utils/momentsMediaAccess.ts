/**
 * 作者：yangleduo
 */
/**
 * 朋友圈图片鉴权访问（本系统 MinIO 图片走服务端中转）。
 */
import {
  buildMomentsImageMediaApiUrl,
  downloadAuthenticatedApi,
  fetchMomentsImageMediaBlobUrl
} from './authDownload'
import { normalizeMediaUrl } from './mediaUrl'
import { isWebEnvironment } from './tokenStorage'

function isDirectMediaUrl(url: string): boolean {
  const v = url.trim()
  return (
    /^https?:\/\//i.test(v) ||
    v.startsWith('blob:') ||
    v.startsWith('data:') ||
    (v.includes('/moments/images/') && v.includes('/content'))
  )
}

export async function resolveMomentsImageDisplaySrc(
  imageId?: string | null,
  fallbackUrl?: string
): Promise<{ src: string; blobUrlToRevoke?: string }> {
  const fallback = (fallbackUrl || '').trim()
  if (fallback && (fallback.startsWith('blob:') || fallback.startsWith('data:'))) {
    return { src: fallback }
  }
  if (imageId?.trim()) {
    if (isWebEnvironment()) {
      return { src: buildMomentsImageMediaApiUrl(imageId) }
    }
    const blobUrl = await fetchMomentsImageMediaBlobUrl(imageId)
    if (blobUrl) {
      return { src: blobUrl, blobUrlToRevoke: blobUrl }
    }
  }
  if (fallback && isDirectMediaUrl(fallback)) {
    return { src: normalizeMediaUrl(fallback) || fallback }
  }
  return { src: normalizeMediaUrl(fallback) || fallback }
}

export function downloadMomentsImageAttachment(imageId: string, fileName: string) {
  return downloadAuthenticatedApi(`/moments/images/${imageId}/content`, fileName)
}
