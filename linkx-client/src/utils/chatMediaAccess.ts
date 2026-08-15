/**
 * 作者：yangleduo
 */
/**
 * 聊天附件安全访问：优先鉴权中转下载，避免预签名 URL 落盘/外传。
 */
import type { ChatMessage } from '../types'
import {
  downloadChatMessageFile,
  buildChatMessageMediaApiUrl,
  fetchChatMessageMediaBlobUrl
} from './authDownload'
import { downloadFileWithSettings, type DownloadOptions, type DownloadResult } from './downloadFile'
import { normalizeMediaUrl } from './mediaUrl'
import { isWebEnvironment } from './tokenStorage'
import { t } from '../i18n'
import {
  getCachedMediaPath,
  saveMediaBytes,
  toMediaFileUrl
} from '../services/chatMessageStore'

/** Electron 鉴权图片 blob 缓存，避免切换会话时重复下载 */
const electronMediaBlobCache = new Map<string, string>()
const MAX_ELECTRON_MEDIA_BLOB_CACHE = 50

export function getCachedElectronMediaBlob(messageId: string): string | undefined {
  return electronMediaBlobCache.get(messageId)
}

export function cacheElectronMediaBlob(messageId: string, blobUrl: string) {
  if (electronMediaBlobCache.has(messageId)) {
    electronMediaBlobCache.delete(messageId)
  } else if (electronMediaBlobCache.size >= MAX_ELECTRON_MEDIA_BLOB_CACHE) {
    const oldest = electronMediaBlobCache.keys().next().value
    if (oldest) {
      const oldUrl = electronMediaBlobCache.get(oldest)
      if (oldUrl) {
        try {
          URL.revokeObjectURL(oldUrl)
        } catch {
          /* ignore */
        }
      }
      electronMediaBlobCache.delete(oldest)
    }
  }
  electronMediaBlobCache.set(messageId, blobUrl)
}

export function clearElectronMediaBlobCache() {
  for (const url of electronMediaBlobCache.values()) {
    try {
      URL.revokeObjectURL(url)
    } catch {
      /* ignore */
    }
  }
  electronMediaBlobCache.clear()
}

/** 是否可走服务端鉴权中转（已入库消息，非发送中/失败乐观项） */
export function canUseAuthenticatedMessageMedia(
  messageId: string | undefined | null,
  sendStatus?: ChatMessage['sendStatus']
): boolean {
  if (!messageId?.trim()) return false
  if (sendStatus === 'sending' || sendStatus === 'failed') return false
  return true
}

export function canUseAuthenticatedChatMedia(msg: ChatMessage): boolean {
  return canUseAuthenticatedMessageMedia(msg.id, msg.sendStatus)
}

function isDirectMediaUrl(url: string): boolean {
  const v = url.trim()
  return (
    /^https?:\/\//i.test(v) ||
    v.startsWith('blob:') ||
    v.startsWith('data:') ||
    v.includes('/chat/messages/') && v.includes('/file')
  )
}

/** 按 messageId 解析可播放/可展示的媒体地址 */
export async function resolveChatMediaSrcByMessageId(
  messageId: string,
  fallbackUrl?: string,
  sendStatus?: ChatMessage['sendStatus']
): Promise<{ src: string; blobUrlToRevoke?: string }> {
  const fallback = (fallbackUrl || '').trim()
  if (fallback.startsWith('blob:') || fallback.startsWith('data:')) {
    return { src: fallback }
  }
  if (canUseAuthenticatedMessageMedia(messageId, sendStatus)) {
    if (isWebEnvironment()) {
      return { src: buildChatMessageMediaApiUrl(messageId) }
    }
    const cached = electronMediaBlobCache.get(messageId)
    if (cached) {
      return { src: cached }
    }
    const disk = await getCachedMediaPath(messageId, 'thumb')
    if (disk) {
      return { src: toMediaFileUrl(disk) }
    }
    const blobUrl = await fetchChatMessageMediaBlobUrl(messageId)
    if (blobUrl) {
      electronMediaBlobCache.set(messageId, blobUrl)
      try {
        const res = await fetch(blobUrl)
        const buf = await res.arrayBuffer()
        const saved = await saveMediaBytes(messageId, buf, { kind: 'thumb', ext: 'jpg' })
        if (saved) {
          return { src: toMediaFileUrl(saved) }
        }
      } catch {
        /* fallback blob */
      }
      return { src: blobUrl }
    }
  }
  if (fallback) {
    return { src: normalizeMediaUrl(fallback) || fallback }
  }
  return { src: '' }
}

export async function resolveChatVoicePlaySrc(msg: ChatMessage): Promise<{
  src: string
  blobUrlToRevoke?: string
}> {
  const fallback = msg.voiceUrl || msg.fileUrl || ''
  return resolveChatMediaSrcByMessageId(msg.id, fallback, msg.sendStatus)
}

/** 图片查看器单条 gallery 项（Web 直出鉴权 URL；Electron 由 viewer 按 messageId 懒加载） */
export function buildChatImageViewerItem(
  msg: ChatMessage,
  conversationId: string,
  imageLabel: string
): {
  url: string
  fileName?: string
  fileSize?: string
  messageId?: string
  conversationId?: string
} {
  const item = {
    url: '',
    fileName: msg.fileName || imageLabel,
    fileSize: msg.fileSize,
    messageId: msg.id,
    conversationId: msg.sessionId || conversationId
  }
  if (isLocalChatMediaPreview(msg)) {
    item.url = (msg.fileUrl || msg.content || '').trim()
    return item
  }
  if (canUseAuthenticatedChatMedia(msg)) {
    if (isWebEnvironment()) {
      item.url = buildChatMessageMediaApiUrl(msg.id)
    }
    return item
  }
  const raw = (msg.content || msg.fileUrl || '').trim()
  if (raw && isDirectMediaUrl(raw)) {
    item.url = normalizeMediaUrl(raw) || raw
  } else {
    item.url = raw
  }
  return item
}

/** 查看器内将 object key / 过期地址解析为可展示 URL */
export async function resolveChatImageViewerItemUrl(item: {
  url?: string
  messageId?: string
}): Promise<{ url: string; blobUrlToRevoke?: string }> {
  const currentUrl = (item.url || '').trim()
  if (currentUrl.startsWith('blob:') || currentUrl.startsWith('data:')) {
    return { url: currentUrl }
  }
  if (item.messageId && canUseAuthenticatedMessageMedia(item.messageId)) {
    const resolved = await resolveChatMediaSrcByMessageId(item.messageId, currentUrl)
    if (resolved.src) {
      return { url: resolved.src, blobUrlToRevoke: resolved.blobUrlToRevoke }
    }
  }
  if (currentUrl && isDirectMediaUrl(currentUrl)) {
    return { url: currentUrl }
  }
  return { url: normalizeMediaUrl(currentUrl) || currentUrl }
}

function resolveAttachmentFileName(msg: ChatMessage): string {
  const fromMeta = (msg.fileName || '').trim()
  if (fromMeta) return fromMeta
  if (msg.type === 'file') {
    const content = (msg.content || '').trim()
    if (content) return content
  }
  if (msg.type === 'image' || msg.isImage) return 'image.png'
  return 'download'
}

function resolveFallbackMediaUrl(msg: ChatMessage): string {
  const raw = (msg.fileUrl || msg.content || '').trim()
  if (!raw) return ''
  if (raw.startsWith('blob:') || raw.startsWith('data:')) return raw
  return normalizeMediaUrl(raw) || raw
}

/** 本地乐观预览（blob/data），不走鉴权 */
export function isLocalChatMediaPreview(msg: ChatMessage): boolean {
  const raw = (msg.fileUrl || msg.content || '').trim()
  return raw.startsWith('blob:') || raw.startsWith('data:')
}

/**
 * 聊天图片展示地址：Web 同源鉴权 URL；Electron fetch+blob；否则预签名回退。
 * @returns blob URL 时由调用方在 onBeforeUnmount 中 revoke
 */
export async function resolveChatImageDisplaySrc(msg: ChatMessage): Promise<{
  src: string
  blobUrlToRevoke?: string
}> {
  if (isLocalChatMediaPreview(msg)) {
    return { src: (msg.fileUrl || msg.content || '').trim() }
  }
  if (canUseAuthenticatedChatMedia(msg)) {
    if (isWebEnvironment()) {
      return { src: buildChatMessageMediaApiUrl(msg.id) }
    }
    const cached = electronMediaBlobCache.get(msg.id)
    if (cached) {
      return { src: cached }
    }
    const blobUrl = await fetchChatMessageMediaBlobUrl(msg.id)
    if (blobUrl) {
      electronMediaBlobCache.set(msg.id, blobUrl)
      return { src: blobUrl }
    }
  }
  const fallback = resolveFallbackMediaUrl(msg) || resolveImagePlaceholderSrc(msg)
  return { src: fallback }
}

function resolveImagePlaceholderSrc(msg: ChatMessage): string {
  return normalizeMediaUrl(msg.content || msg.fileUrl) || msg.content || msg.fileUrl || ''
}

/**
 * 下载或打开聊天附件：已发送消息走鉴权 API；本地 blob/data 走直连。
 */
export async function downloadChatMessageAttachment(
  msg: ChatMessage,
  options: DownloadOptions = {}
): Promise<DownloadResult> {
  const fileName = resolveAttachmentFileName(msg)

  if (canUseAuthenticatedChatMedia(msg)) {
    const authResult = await downloadChatMessageFile(msg.id, fileName, options)
    if (authResult.ok || authResult.canceled) return authResult
  }

  const url = resolveFallbackMediaUrl(msg)
  if (!url) {
    return { ok: false, message: t('chat.fileOpenMissing') }
  }

  return downloadFileWithSettings(url, fileName, options)
}
