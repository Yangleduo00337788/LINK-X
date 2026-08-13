/**
 * 作者：yangleduo
 */
/**
 * 群相册/群文件鉴权媒体访问。
 */
import {
  buildGroupAssetMediaApiUrl,
  downloadGroupAssetContent,
  fetchGroupAssetMediaBlobUrl
} from './authDownload'
import { normalizeMediaUrl } from './mediaUrl'
import { isWebEnvironment } from './tokenStorage'

export async function resolveGroupAssetDisplaySrc(
  conversationId: string,
  assetId: string,
  fallbackUrl?: string
): Promise<{ src: string; blobUrlToRevoke?: string }> {
  if (!conversationId?.trim() || !assetId?.trim()) {
    const fallback = normalizeMediaUrl(fallbackUrl || '') || fallbackUrl || ''
    return { src: fallback }
  }
  if (isWebEnvironment()) {
    return { src: buildGroupAssetMediaApiUrl(conversationId, assetId) }
  }
  const blobUrl = await fetchGroupAssetMediaBlobUrl(conversationId, assetId)
  if (blobUrl) {
    return { src: blobUrl, blobUrlToRevoke: blobUrl }
  }
  const fallback = normalizeMediaUrl(fallbackUrl || '') || fallbackUrl || ''
  return { src: fallback }
}

export function downloadGroupAssetAttachment(
  conversationId: string,
  assetId: string,
  fileName: string
) {
  return downloadGroupAssetContent(conversationId, assetId, fileName)
}
