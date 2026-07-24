import { apiClient } from './client'
import type { ApiResult } from '../types/auth'
import type { ChatFileUploadResult, ConversationItem, MessageItem } from '../types/chat'

export function listSessions() {
  return apiClient.get<unknown, ApiResult<ConversationItem[]>>('/chat/sessions')
}

export function openPrivateChat(friendId: string) {
  return apiClient.post<unknown, ApiResult<ConversationItem>>(`/chat/private/${friendId}`)
}

export function listMessages(conversationId: string, before?: string, limit = 50) {
  return apiClient.get<unknown, ApiResult<MessageItem[]>>(`/chat/sessions/${conversationId}/messages`, {
    params: { before, limit }
  })
}

export function uploadChatFile(conversationId: string, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return apiClient.post<unknown, ApiResult<ChatFileUploadResult>>(
    `/chat/sessions/${conversationId}/upload`,
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 60000 }
  )
}

/** 分片大小：5MiB（与后端 ComposeObject 约束一致） */
export const CHAT_UPLOAD_PART_SIZE = 5 * 1024 * 1024

export interface CheckHashResult {
  exists: boolean
  objectKey?: string
  url?: string
  fileName?: string
  fileSize?: number
  contentType?: string
}

export function checkFileHash(payload: {
  hash: string
  fileName?: string
  fileSize?: number
  contentType?: string
}) {
  return apiClient.post<unknown, ApiResult<CheckHashResult>>('/chat/upload/check-hash', payload)
}

export function initMultipartUpload(
  conversationId: string,
  payload: { fileName: string; contentType: string; fileSize: number }
) {
  return apiClient.post<
    unknown,
    ApiResult<{ uploadId: string; objectName: string; partSize: number }>
  >(`/chat/sessions/${conversationId}/upload/init`, payload)
}

export function uploadMultipartPart(
  conversationId: string,
  params: { objectName: string; uploadId: string; partNumber: number; blob: Blob }
) {
  const formData = new FormData()
  formData.append('file', params.blob, `part-${params.partNumber}`)
  return apiClient.post<unknown, ApiResult<{ etag: string; partNumber: string }>>(
    `/chat/sessions/${conversationId}/upload/part`,
    formData,
    {
      params: {
        objectName: params.objectName,
        uploadId: params.uploadId,
        partNumber: params.partNumber
      },
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000
    }
  )
}

export function listMultipartParts(conversationId: string, uploadId: string) {
  return apiClient.get<unknown, ApiResult<Array<{ partNumber: number; etag: string }>>>(
    `/chat/sessions/${conversationId}/upload/${uploadId}/parts`
  )
}

export function completeMultipartUpload(
  conversationId: string,
  payload: {
    objectName: string
    uploadId: string
    parts: Array<{ partNumber: number; etag: string }>
    fileName: string
    fileSize: number
    contentType: string
    contentHash?: string
  }
) {
  return apiClient.post<unknown, ApiResult<ChatFileUploadResult>>(
    `/chat/sessions/${conversationId}/upload/complete`,
    payload
  )
}

export function abortMultipartUpload(
  conversationId: string,
  payload: { objectName: string; uploadId: string }
) {
  return apiClient.post<unknown, ApiResult<null>>(
    `/chat/sessions/${conversationId}/upload/abort`,
    payload
  )
}

/** 计算文件 SHA-256（小写 hex） */
export async function sha256File(file: Blob): Promise<string> {
  const buffer = await file.arrayBuffer()
  const hash = await crypto.subtle.digest('SHA-256', buffer)
  return Array.from(new Uint8Array(hash))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

/**
 * 智能上传：秒传 → 大文件分片 → 普通单次上传。
 * 分片失败会自动 abort；已上传分片可幂等重试。
 */
export async function uploadChatFileSmart(
  conversationId: string,
  file: File
): Promise<ApiResult<ChatFileUploadResult>> {
  const contentType = file.type || 'application/octet-stream'
  let contentHash: string | undefined
  try {
    contentHash = await sha256File(file)
    const check = await checkFileHash({
      hash: contentHash,
      fileName: file.name,
      fileSize: file.size,
      contentType
    })
    if (check.code === 200 && check.data?.exists && check.data.url) {
      return {
        code: 200,
        message: check.message || 'ok',
        data: {
          url: check.data.url,
          fileKey: check.data.objectKey,
          fileName: check.data.fileName || file.name,
          fileSize: check.data.fileSize ?? file.size,
          contentType: check.data.contentType || contentType
        }
      }
    }
  } catch {
    // 秒传探测失败时降级为实际上传
  }

  if (file.size > CHAT_UPLOAD_PART_SIZE) {
    return uploadChatFileMultipart(conversationId, file, contentHash)
  }
  return uploadChatFile(conversationId, file)
}

async function uploadChatFileMultipart(
  conversationId: string,
  file: File,
  contentHash?: string
): Promise<ApiResult<ChatFileUploadResult>> {
  const contentType = file.type || 'application/octet-stream'
  const init = await initMultipartUpload(conversationId, {
    fileName: file.name,
    contentType,
    fileSize: file.size
  })
  if (init.code !== 200 || !init.data?.uploadId || !init.data.objectName) {
    throw new Error(init.message || '初始化分片上传失败')
  }

  const { uploadId, objectName } = init.data
  const partSize = init.data.partSize > 0 ? init.data.partSize : CHAT_UPLOAD_PART_SIZE
  const parts: Array<{ partNumber: number; etag: string }> = []

  try {
    // 断点：拉取已上传分片
    const existing = await listMultipartParts(conversationId, uploadId)
    const done = new Map<number, string>()
    if (existing.code === 200 && existing.data) {
      for (const p of existing.data) {
        done.set(p.partNumber, p.etag)
      }
    }

    const totalParts = Math.ceil(file.size / partSize)
    for (let partNumber = 1; partNumber <= totalParts; partNumber++) {
      if (done.has(partNumber)) {
        parts.push({ partNumber, etag: done.get(partNumber)! })
        continue
      }
      const start = (partNumber - 1) * partSize
      const end = Math.min(start + partSize, file.size)
      const blob = file.slice(start, end)
      const partRes = await uploadMultipartPart(conversationId, {
        objectName,
        uploadId,
        partNumber,
        blob
      })
      if (partRes.code !== 200 || !partRes.data?.etag) {
        throw new Error(partRes.message || `分片 ${partNumber} 上传失败`)
      }
      parts.push({ partNumber, etag: partRes.data.etag })
    }

    return completeMultipartUpload(conversationId, {
      objectName,
      uploadId,
      parts,
      fileName: file.name,
      fileSize: file.size,
      contentType,
      contentHash
    })
  } catch (err) {
    try {
      await abortMultipartUpload(conversationId, { objectName, uploadId })
    } catch {
      // ignore abort errors
    }
    throw err
  }
}

export interface ChatSearchHit {
  messageId: string
  conversationId: string
  conversationName?: string
  conversationType?: number
  senderId?: string
  senderNickname?: string
  type: string
  content?: string
  fileName?: string
  fileUrl?: string
  createTime?: number
  /** 已转义 HTML，关键词包在 <mark> 中 */
  highlight?: string
}

export function searchMessages(
  q: string,
  opts?: {
    type?: string
    conversationId?: string
    limit?: number
    fromTime?: number
    toTime?: number
  }
) {
  return apiClient.get<unknown, ApiResult<ChatSearchHit[]>>('/chat/search', {
    params: {
      q,
      type: opts?.type,
      conversationId: opts?.conversationId,
      limit: opts?.limit ?? 50,
      fromTime: opts?.fromTime,
      toTime: opts?.toTime
    }
  })
}

export function recallMessage(conversationId: string, messageId: string) {
  return apiClient.post<unknown, ApiResult<MessageItem>>(
    `/chat/sessions/${conversationId}/messages/${messageId}/recall`
  )
}

export function editMessage(conversationId: string, messageId: string, content: string) {
  return apiClient.post<unknown, ApiResult<MessageItem>>(
    `/chat/sessions/${conversationId}/messages/${messageId}/edit`,
    { content }
  )
}

export function forwardMessage(
  conversationId: string,
  messageId: string,
  targetConversationId: string
) {
  return apiClient.post<unknown, ApiResult<MessageItem>>(
    `/chat/sessions/${conversationId}/messages/${messageId}/forward`,
    { targetConversationId }
  )
}

export interface QuoteMessageBody {
  conversationId: string | number
  msgType: 'text' | 'image' | 'file' | 'voice'
  content?: string
  fileName?: string
  fileSize?: string | number
  fileUrl?: string
  voiceDuration?: number
  clientMsgId?: string
}

export function quoteMessage(
  conversationId: string,
  messageId: string,
  body: QuoteMessageBody
) {
  return apiClient.post<unknown, ApiResult<MessageItem>>(
    `/chat/sessions/${conversationId}/messages/${messageId}/quote`,
    body
  )
}

export interface MessageReadCount {
  readCount: number
  totalMembers: number
}

export function getMessageReadCount(conversationId: string, messageId: string) {
  return apiClient.get<unknown, ApiResult<MessageReadCount>>(
    `/chat/sessions/${conversationId}/messages/${messageId}/read-count`
  )
}

/** 上报会话已读游标，服务端清未读并广播 readReceipt */
export function markAsRead(conversationId: string, lastMessageId: string) {
  return apiClient.post<unknown, ApiResult<number>>(
    `/chat/sessions/${conversationId}/read`,
    null,
    { params: { lastMessageId } }
  )
}

/** 重新签发消息媒体预签名 URL（过期自愈） */
export function refreshMessageMediaUrl(messageId: string) {
  return apiClient.get<unknown, ApiResult<{ url: string }>>(
    `/chat/messages/${messageId}/media-url`
  )
}

export function togglePin(conversationId: string) {
  return apiClient.post<unknown, ApiResult<null>>(`/chat/sessions/${conversationId}/pin`)
}

export function toggleImportant(conversationId: string) {
  return apiClient.post<unknown, ApiResult<null>>(`/chat/sessions/${conversationId}/important`)
}

export function toggleMute(conversationId: string) {
  return apiClient.post<unknown, ApiResult<null>>(`/chat/sessions/${conversationId}/mute`)
}

export function saveDraft(conversationId: string, content: string) {
  return apiClient.post<unknown, ApiResult<null>>(`/chat/sessions/${conversationId}/draft`, {
    content
  })
}

export function getDraft(conversationId: string) {
  return apiClient.get<unknown, ApiResult<string>>(`/chat/sessions/${conversationId}/draft`)
}
