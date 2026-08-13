/**
 * 作者：yangleduo
 */
/**
 * 带登录态的 API 资源下载（后端中转，不走 MinIO 预签名）。
 */

import { getToken, isWebEnvironment } from './tokenStorage'
import { useAppSettingsStore } from '../stores/appSettings'
import type { DownloadOptions, DownloadResult } from './downloadFile'
import { API_BASE_URL } from '../config/endpoints'
import { t } from '../i18n'

const apiBase = API_BASE_URL

async function authFetch(apiPath: string): Promise<Response | null> {
  const isWeb = isWebEnvironment()
  const token = await getToken('accessToken')
  if (!isWeb && !token) {
    return null
  }
  const path = apiPath.startsWith('/') ? apiPath : `/${apiPath}`
  const url = new URL(`${apiBase}${path}`)
  const headers: Record<string, string> = {}
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  try {
    const res = await fetch(url.toString(), {
      headers,
      credentials: isWeb ? 'include' : 'same-origin'
    })
    return res
  } catch {
    return null
  }
}

/** 鉴权拉取二进制并返回 blob URL（调用方负责 revoke） */
export async function fetchAuthenticatedApiBlobUrl(apiPath: string): Promise<string | null> {
  const res = await authFetch(apiPath)
  if (!res || !res.ok) return null
  try {
    const blob = await res.blob()
    return URL.createObjectURL(blob)
  } catch {
    return null
  }
}

/** 聊天消息附件鉴权 URL（Web Cookie / Electron fetch+blob） */
export function buildChatMessageMediaApiUrl(messageId: string): string {
  return `${apiBase}/chat/messages/${encodeURIComponent(messageId)}/file`
}

export async function fetchChatMessageMediaBlobUrl(messageId: string): Promise<string | null> {
  return fetchAuthenticatedApiBlobUrl(`/chat/messages/${messageId}/file`)
}

/** 网盘文件鉴权 URL */
export function buildDriveFileMediaApiUrl(fileId: string): string {
  return `${apiBase}/cloud/files/${encodeURIComponent(fileId)}/content`
}

export async function fetchDriveFileMediaBlobUrl(fileId: string): Promise<string | null> {
  return fetchAuthenticatedApiBlobUrl(`/cloud/files/${fileId}/content`)
}

/**
 * 下载需鉴权的后端中转地址，例如 `/cloud/files/{id}/content`。
 */
export async function downloadAuthenticatedApi(
  apiPath: string,
  fileName: string,
  query?: Record<string, string | undefined>,
  options: DownloadOptions = {}
): Promise<DownloadResult> {
  const isWeb = isWebEnvironment()
  const token = await getToken('accessToken')
  // Web 环境 token 在 HttpOnly Cookie 中（本地不可读），仍可凭 Cookie 下载；
  // Electron 环境必须有本地 token 走 Authorization Header。
  if (!isWeb && !token) {
    return { ok: false, message: t('errors.wsNotLoggedIn') }
  }

  const path = apiPath.startsWith('/') ? apiPath : `/${apiPath}`
  const url = new URL(`${apiBase}${path}`)
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v != null && v !== '') url.searchParams.set(k, v)
    }
  }

  try {
    const headers: Record<string, string> = {}
    if (token) {
      headers.Authorization = `Bearer ${token}`
    }
    const res = await fetch(url.toString(), {
      headers,
      // Web 环境携带 HttpOnly Cookie 完成鉴权；Electron 走 Authorization Header，用 same-origin 即可
      credentials: isWeb ? 'include' : 'same-origin'
    })
    if (!res.ok) {
      return { ok: false, message: t('errors.downloadFailedWithStatus', { status: String(res.status) }) }
    }
    const blob = await res.blob()
    const objectUrl = URL.createObjectURL(blob)
    try {
      const settings = useAppSettingsStore()
      const directory = (settings.downloadPath || '').trim() || undefined
      const askEveryTime = options.openAfter ? false : !!settings.downloadAskEveryTime
      const name = (fileName || 'download').trim() || 'download'
      const api = window.electronAPI?.downloadFile
      if (api) {
        // 走主进程：把 blob 转 ArrayBuffer
        const buf = await blob.arrayBuffer()
        return await api({
          data: buf,
          fileName: name,
          directory,
          askEveryTime,
          openAfter: options.openAfter
        })
      }
      if (options.openAfter) {
        window.open(objectUrl, '_blank', 'noopener,noreferrer')
        return { ok: true }
      }
      const a = document.createElement('a')
      a.href = objectUrl
      a.download = name
      a.click()
      return { ok: true }
    } finally {
      URL.revokeObjectURL(objectUrl)
    }
  } catch (e) {
    return { ok: false, message: e instanceof Error ? e.message : t('errors.downloadFailed') }
  }
}

/** 网盘文件中转下载 */
export function downloadDriveFileContent(fileId: string, fileName: string) {
  return downloadAuthenticatedApi(`/cloud/files/${fileId}/content`, fileName)
}

/** 聊天消息附件中转下载 */
export function downloadChatMessageFile(
  messageId: string,
  fileName: string,
  options?: DownloadOptions
) {
  return downloadAuthenticatedApi(`/chat/messages/${messageId}/file`, fileName, undefined, options)
}

/** 群资源中转下载 */
export function buildGroupAssetMediaApiUrl(conversationId: string, assetId: string): string {
  return `${apiBase}/group/${encodeURIComponent(conversationId)}/assets/${encodeURIComponent(assetId)}/content`
}

export async function fetchGroupAssetMediaBlobUrl(
  conversationId: string,
  assetId: string
): Promise<string | null> {
  return fetchAuthenticatedApiBlobUrl(
    `/group/${conversationId}/assets/${assetId}/content`
  )
}

/** 朋友圈图片鉴权 URL */
export function buildMomentsImageMediaApiUrl(imageId: string): string {
  return `${apiBase}/moments/images/${encodeURIComponent(imageId)}/content`
}

export async function fetchMomentsImageMediaBlobUrl(imageId: string): Promise<string | null> {
  return fetchAuthenticatedApiBlobUrl(`/moments/images/${imageId}/content`)
}

export function downloadGroupAssetContent(
  conversationId: string,
  assetId: string,
  fileName: string
) {
  return downloadAuthenticatedApi(
    `/group/${conversationId}/assets/${assetId}/content`,
    fileName
  )
}

/** 公开分享中转下载（可免登录，可选提取码） */
export async function downloadShareContent(
  token: string,
  fileName: string,
  password?: string
): Promise<DownloadResult> {
  const url = new URL(`${apiBase}/cloud/share/${encodeURIComponent(token)}/content`)
  if (password) url.searchParams.set('password', password)
  try {
    const res = await fetch(url.toString())
    if (!res.ok) {
      return { ok: false, message: t('errors.downloadFailedWithStatus', { status: String(res.status) }) }
    }
    const blob = await res.blob()
    const objectUrl = URL.createObjectURL(blob)
    try {
      const a = document.createElement('a')
      a.href = objectUrl
      a.download = (fileName || 'download').trim() || 'download'
      a.click()
      return { ok: true }
    } finally {
      URL.revokeObjectURL(objectUrl)
    }
  } catch (e) {
    return { ok: false, message: e instanceof Error ? e.message : t('errors.downloadFailed') }
  }
}
