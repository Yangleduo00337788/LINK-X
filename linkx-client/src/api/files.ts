import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface CloudFileVO {
  id: string
  source: 'chat_message' | 'group_asset'
  title?: string
  fileName?: string
  fileSize?: number
  fileUrl?: string
  category?: 'document' | 'image' | 'media' | 'other'
  conversationId?: string
  conversationName?: string
  senderName?: string
  createTime?: number
}

export function listCloudFiles(category?: string, limit = 100) {
  return apiClient.get<never, ApiResult<CloudFileVO[]>>('/files', {
    params: { category, limit }
  })
}
