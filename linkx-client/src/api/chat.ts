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
}

export function searchMessages(q: string, opts?: { type?: string; conversationId?: string; limit?: number }) {
  return apiClient.get<unknown, ApiResult<ChatSearchHit[]>>('/chat/search', {
    params: {
      q,
      type: opts?.type,
      conversationId: opts?.conversationId,
      limit: opts?.limit ?? 50
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

export function togglePin(conversationId: string) {
  return apiClient.post<unknown, ApiResult<null>>(`/chat/sessions/${conversationId}/pin`)
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
