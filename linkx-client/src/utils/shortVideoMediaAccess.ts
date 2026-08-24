/**
 * 作者：yangleduo
 */
/**
 * 短视频鉴权媒体：统一走 API 流式地址（Electron 主进程注入 Authorization）。
 */
import { normalizeMediaUrl } from './mediaUrl'
import { API_BASE_URL } from '../config/endpoints'

export function buildShortVideoMediaApiUrl(postId: string, kind: 'video' | 'cover' = 'video'): string {
  const path =
    kind === 'video'
      ? `/short-video/${encodeURIComponent(postId)}/video/content`
      : `/short-video/${encodeURIComponent(postId)}/cover/content`
  return `${API_BASE_URL}${path}`
}

export function buildShortVideoCommentImageUrl(commentId: string): string {
  return `${API_BASE_URL}/short-video/comment/${encodeURIComponent(commentId)}/image/content`
}

/** 评论图片鉴权流地址（后端 imageUrl 多为相对路径，需拼 API 基址）。 */
export function resolveShortVideoCommentImageSrc(comment: {
  id: string
  imageUrl?: string | null
}): string | null {
  const marker = comment.imageUrl?.trim()
  if (!marker) return null
  if (
    marker.startsWith('http://') ||
    marker.startsWith('https://') ||
    marker.startsWith('blob:') ||
    marker.startsWith('data:')
  ) {
    return normalizeMediaUrl(marker)
  }
  if (marker.startsWith('/')) {
    return `${API_BASE_URL}${marker}`
  }
  return buildShortVideoCommentImageUrl(comment.id)
}

export async function resolveShortVideoDisplaySrc(
  postId: string,
  kind: 'video' | 'cover' = 'video',
  fallbackUrl?: string
): Promise<{ src: string; blobUrlToRevoke?: string }> {
  const id = postId?.trim()
  if (id) {
    return { src: buildShortVideoMediaApiUrl(id, kind) }
  }
  const fallback = (fallbackUrl || '').trim()
  if (fallback.startsWith('blob:') || fallback.startsWith('data:')) {
    return { src: fallback }
  }
  const normalized = normalizeMediaUrl(fallback)
  return { src: normalized || fallback }
}
