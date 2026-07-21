import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export type GroupAssetType = 'file' | 'image' | 'essence'

export interface GroupAssetVO {
  id: string
  conversationId: string
  type: GroupAssetType
  title?: string
  content?: string
  fileName?: string
  fileSize?: number
  fileUrl?: string
  downloadCount?: number
  messageId?: string
  uploaderId?: string
  uploaderNickname?: string
  createTime?: string
}

export interface CreateEssencePayload {
  type: 'essence'
  title?: string
  content: string
  messageId?: number | string
}

export function listGroupAssets(conversationId: string, type?: GroupAssetType) {
  return apiClient.get<never, ApiResult<GroupAssetVO[]>>(`/group/${conversationId}/assets`, {
    params: type ? { type } : undefined
  })
}

export function uploadGroupAsset(
  conversationId: string,
  type: 'file' | 'image',
  file: File,
  album?: string
) {
  const form = new FormData()
  form.append('file', file)
  // 不手动设 Content-Type，由浏览器带上 multipart boundary
  return apiClient.post<never, ApiResult<GroupAssetVO>>(
    `/group/${conversationId}/assets/upload`,
    form,
    {
      params: {
        type,
        ...(album ? { album } : {})
      },
      timeout: 60000
    }
  )
}

export function createGroupEssence(conversationId: string, payload: CreateEssencePayload) {
  return apiClient.post<never, ApiResult<GroupAssetVO>>(`/group/${conversationId}/assets`, payload)
}

export function deleteGroupAsset(conversationId: string, assetId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/group/${conversationId}/assets/${assetId}`)
}
