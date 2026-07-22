import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface DriveStorageVO {
  usedBytes: number
  quotaBytes: number
  fileCount: number
  usedPercent: number
}

export interface DriveItemVO {
  kind: 'folder' | 'file'
  id: string
  name: string
  parentId?: string | null
  folderId?: string | null
  fileSize?: number
  fileUrl?: string
  contentType?: string
  ext?: string
  category?: 'document' | 'image' | 'media' | 'other'
  description?: string
  childCount?: number
  tags?: string[]
  uploaderName?: string
  createTime?: number
  updateTime?: number
}

export interface DriveActivityVO {
  id: string
  targetType: string
  targetId: string
  targetName?: string
  action: string
  detail?: string
  createTime?: number
}

export interface DriveShareVO {
  id: string
  shareType: 'file' | 'folder'
  targetId: string
  token: string
  shareUrl: string
  hasPassword: boolean
  expireAt?: number
  maxDownloads?: number
  downloadCount?: number
  targetName?: string
  fileSize?: number
  fileUrl?: string
}

export function getDriveStorage() {
  return apiClient.get<never, ApiResult<DriveStorageVO>>('/cloud/storage')
}

export function expandDriveStorage() {
  return apiClient.post<never, ApiResult<DriveStorageVO>>('/cloud/storage/expand')
}

export function listDriveItems(folderId?: string | null, keyword?: string) {
  return apiClient.get<never, ApiResult<DriveItemVO[]>>('/cloud/items', {
    params: {
      folderId: folderId || undefined,
      keyword: keyword || undefined
    }
  })
}

export function getDriveBreadcrumb(folderId?: string | null) {
  return apiClient.get<never, ApiResult<DriveItemVO[]>>('/cloud/breadcrumb', {
    params: { folderId: folderId || undefined }
  })
}

export function createDriveFolder(name: string, parentId?: string | null) {
  return apiClient.post<never, ApiResult<DriveItemVO>>('/cloud/folders', {
    name,
    parentId: parentId || undefined
  })
}

export function uploadDriveFile(file: File, folderId?: string | null) {
  const form = new FormData()
  form.append('file', file)
  return apiClient.post<never, ApiResult<DriveItemVO>>('/cloud/files/upload', form, {
    params: { folderId: folderId || undefined },
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

export function getDriveFile(fileId: string) {
  return apiClient.get<never, ApiResult<DriveItemVO>>(`/cloud/files/${fileId}`)
}

export function updateDriveFile(
  fileId: string,
  body: { name?: string; folderId?: string; description?: string }
) {
  return apiClient.patch<never, ApiResult<DriveItemVO>>(`/cloud/files/${fileId}`, body)
}

export function deleteDriveFile(fileId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/cloud/files/${fileId}`)
}

export function updateDriveFolder(
  folderId: string,
  body: { name?: string; folderId?: string }
) {
  return apiClient.patch<never, ApiResult<DriveItemVO>>(`/cloud/folders/${folderId}`, body)
}

export function deleteDriveFolder(folderId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/cloud/folders/${folderId}`)
}

export function batchDeleteDriveItems(items: { kind: string; id: string }[]) {
  return apiClient.post<never, ApiResult<null>>('/cloud/items/batch-delete', { items })
}

export function batchMoveDriveItems(items: { kind: string; id: string }[], targetFolderId?: string | null) {
  return apiClient.post<never, ApiResult<null>>('/cloud/items/batch-move', {
    items,
    targetFolderId: targetFolderId ?? ''
  })
}

export function addDriveTag(fileId: string, tagName: string) {
  return apiClient.post<never, ApiResult<string[]>>(`/cloud/files/${fileId}/tags`, { tagName })
}

export function removeDriveTag(fileId: string, tagName: string) {
  return apiClient.delete<never, ApiResult<string[]>>(
    `/cloud/files/${fileId}/tags/${encodeURIComponent(tagName)}`
  )
}

export function listDriveActivities(fileId?: string, limit = 50) {
  return apiClient.get<never, ApiResult<DriveActivityVO[]>>('/cloud/activities', {
    params: { fileId: fileId || undefined, limit }
  })
}

export function createDriveShare(body: {
  shareType: 'file' | 'folder'
  targetId: string
  password?: string
  expireHours?: number
  maxDownloads?: number
}) {
  return apiClient.post<never, ApiResult<DriveShareVO>>('/cloud/shares', body)
}

export function revokeDriveShare(shareId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/cloud/shares/${shareId}`)
}
