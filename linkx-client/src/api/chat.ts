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
