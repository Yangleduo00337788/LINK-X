/**
 * 带登录态的 API 资源下载（后端中转，不走 MinIO 预签名）。
 */

import { getToken, isWebEnvironment } from './tokenStorage'
import { useAppSettingsStore } from '../stores/appSettings'
import type { DownloadResult } from './downloadFile'
import { API_BASE_URL } from '../config/endpoints'

const apiBase = API_BASE_URL

/**
 * 下载需鉴权的后端中转地址，例如 `/cloud/files/{id}/content`。
 */
export async function downloadAuthenticatedApi(
  apiPath: string,
  fileName: string,
  query?: Record<string, string | undefined>
): Promise<DownloadResult> {
  const isWeb = isWebEnvironment()
  const token = await getToken('accessToken')
  // Web 环境 token 在 HttpOnly Cookie 中（本地不可读），仍可凭 Cookie 下载；
  // Electron 环境必须有本地 token 走 Authorization Header。
  if (!isWeb && !token) {
    return { ok: false, message: '未登录' }
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
      return { ok: false, message: `下载失败 (${res.status})` }
    }
    const blob = await res.blob()
    const objectUrl = URL.createObjectURL(blob)
    try {
      const settings = useAppSettingsStore()
      const directory = (settings.downloadPath || '').trim() || undefined
      const askEveryTime = !!settings.downloadAskEveryTime
      const name = (fileName || 'download').trim() || 'download'
      const api = window.electronAPI?.downloadFile
      if (api) {
        // 走主进程：把 blob 转 ArrayBuffer
        const buf = await blob.arrayBuffer()
        return await api({
          data: buf,
          fileName: name,
          directory,
          askEveryTime
        })
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
    return { ok: false, message: e instanceof Error ? e.message : '下载失败' }
  }
}

/** 网盘文件中转下载 */
export function downloadDriveFileContent(fileId: string, fileName: string) {
  return downloadAuthenticatedApi(`/cloud/files/${fileId}/content`, fileName)
}

/** 聊天消息附件中转下载 */
export function downloadChatMessageFile(messageId: string, fileName: string) {
  return downloadAuthenticatedApi(`/chat/messages/${messageId}/file`, fileName)
}

/** 群资源中转下载 */
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
      return { ok: false, message: `下载失败 (${res.status})` }
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
    return { ok: false, message: e instanceof Error ? e.message : '下载失败' }
  }
}
