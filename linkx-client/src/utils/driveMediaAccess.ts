/**
 * 作者：yangleduo
 */
/**
 * 个人网盘图片鉴权展示（缩略图/预览）。
 */
import {
  buildDriveFileMediaApiUrl,
  fetchDriveFileMediaBlobUrl
} from './authDownload'
import { normalizeMediaUrl } from './mediaUrl'
import { isWebEnvironment } from './tokenStorage'

export async function resolveDriveImageDisplaySrc(
  fileId: string,
  fallbackUrl?: string
): Promise<{ src: string; blobUrlToRevoke?: string }> {
  const fallback = (fallbackUrl || '').trim()
  if (fallback.startsWith('blob:') || fallback.startsWith('data:')) {
    return { src: fallback }
  }
  if (fileId?.trim()) {
    if (isWebEnvironment()) {
      return { src: buildDriveFileMediaApiUrl(fileId) }
    }
    const blobUrl = await fetchDriveFileMediaBlobUrl(fileId)
    if (blobUrl) {
      return { src: blobUrl, blobUrlToRevoke: blobUrl }
    }
  }
  if (fallback) {
    return { src: normalizeMediaUrl(fallback) || fallback }
  }
  return { src: '' }
}
