/**
 * 作者：yangleduo
 */
import { del, get, post, put } from './request'
import request from './request'
import type { PageQuery, PageResult } from '@/types/api'

export type VersionPlatform = 'windows' | 'macos' | 'linux'

export interface VersionItem {
  id: string
  version: string
  channel: string
  platform?: VersionPlatform
  releaseNotes?: string
  downloadKey?: string
  downloadUrl?: string
  packageSha256?: string
  packageFileName?: string
  packageSize?: number
  forceUpdate?: boolean
  minSupportedVersion?: string
  status?: string
  publishedAt?: string
  publishedBy?: string
  createdBy?: string
  updatedBy?: string
  createTime?: string
  updateTime?: string
}

export interface VersionPayload {
  version: string
  channel: string
  platform: VersionPlatform
  releaseNotes?: string
  downloadUrl?: string
  packageSha256?: string
  packageFileName?: string
  packageSize?: number
  forceUpdate: boolean
  minSupportedVersion?: string
}

export interface VersionUploadResult {
  objectKey: string
  url: string
  sha256?: string
  fileName?: string
  fileSize?: number
}

export interface VersionQuery extends PageQuery {
  versionStatus?: string
  channel?: string
  platform?: VersionPlatform | ''
}

/** 超过此大小走分片上传（每片 8MB） */
const MULTIPART_UPLOAD_THRESHOLD = 20 * 1024 * 1024
const CHUNK_SIZE = 8 * 1024 * 1024
/** 单片/整包上传超时（30 分钟） */
const UPLOAD_TIMEOUT_MS = 30 * 60 * 1000
/** 分片并发数（OSS 合并要求非末片 ≥5MB，单片 8MB） */
const PART_UPLOAD_CONCURRENCY = 3

/** 开发环境大文件 multipart 直连接后端，绕开 Vite 代理对 >1MB 体的 ECONNRESET */
function resolveDirectApiBase(): string | undefined {
  const configured = (import.meta.env.VITE_API_DIRECT_URL as string | undefined)?.trim()
  if (configured) {
    return configured.replace(/\/$/, '')
  }
  const envBase = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.trim()
  if (envBase && /^https?:\/\//i.test(envBase)) {
    return envBase.replace(/\/$/, '')
  }
  if (import.meta.env.DEV && typeof window !== 'undefined') {
    return `http://${window.location.hostname}:8080/api`
  }
  return undefined
}

async function sha256HexOfFile(file: File): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer())
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

async function postMultipart(
  url: string,
  formData: FormData,
  options?: { direct?: boolean }
) {
  const directBase = options?.direct ? resolveDirectApiBase() : undefined
  const { data } = await request.post<{ code: number; message?: string }>(url, formData, {
    timeout: UPLOAD_TIMEOUT_MS,
    ...(directBase ? { baseURL: directBase } : {}),
  })
  return data
}

export function listVersions(params: VersionQuery) {
  return get<PageResult<VersionItem>>('/admin/versions', params as Record<string, unknown>)
}

export function getVersion(id: string) {
  return get<VersionItem>(`/admin/versions/${id}`)
}

export function createVersion(payload: VersionPayload) {
  return post<VersionItem>('/admin/versions', payload)
}

export function updateVersion(id: string, payload: VersionPayload) {
  return put<VersionItem>(`/admin/versions/${id}`, payload)
}

export function deleteVersion(id: string) {
  return del<void>(`/admin/versions/${id}`)
}

export function publishVersion(id: string) {
  return post<VersionItem>(`/admin/versions/${id}/publish`)
}

async function uploadVersionPackageSingle(file: File): Promise<VersionUploadResult> {
  const formData = new FormData()
  formData.append('file', file)
  const directBase = resolveDirectApiBase()
  const { data } = await request.post<{ code: number; data: VersionUploadResult; message?: string }>(
    '/admin/versions/upload',
    formData,
    {
      timeout: UPLOAD_TIMEOUT_MS,
      ...(directBase ? { baseURL: directBase } : {}),
    }
  )
  if (data.code !== 200 || !data.data) {
    throw new Error(data.message || 'upload failed')
  }
  return data.data
}

async function uploadInstallerPart(
  file: File,
  uploadId: string,
  objectKey: string,
  partNumber: number,
  totalParts: number
) {
  const start = (partNumber - 1) * CHUNK_SIZE
  const chunk = file.slice(start, Math.min(start + CHUNK_SIZE, file.size))
  const formData = new FormData()
  formData.append('file', chunk, file.name)
  formData.append('uploadId', uploadId)
  formData.append('objectKey', objectKey)
  formData.append('partNumber', String(partNumber))
  const data = await postMultipart('/admin/versions/upload/multipart/part', formData, { direct: true })
  if (data.code !== 200) {
    throw new Error(data.message || `分片 ${partNumber}/${totalParts} 上传失败`)
  }
}

async function uploadVersionPackageChunked(file: File): Promise<VersionUploadResult> {
  const sha256Promise = sha256HexOfFile(file)
  const initRes = await post<{ uploadId: string; objectKey: string }>(
    '/admin/versions/upload/multipart/init',
    { fileName: file.name },
    { timeout: UPLOAD_TIMEOUT_MS }
  )
  const { uploadId, objectKey } = initRes
  const totalParts = Math.ceil(file.size / CHUNK_SIZE)
  let nextPart = 1
  const workers = Array.from({ length: PART_UPLOAD_CONCURRENCY }, async () => {
    while (true) {
      const partNumber = nextPart
      nextPart += 1
      if (partNumber > totalParts) {
        return
      }
      await uploadInstallerPart(file, uploadId, objectKey, partNumber, totalParts)
    }
  })
  await Promise.all(workers)
  const packageSha256 = await sha256Promise
  return post<VersionUploadResult>(
    '/admin/versions/upload/multipart/complete',
    {
      uploadId,
      objectKey,
      fileName: file.name,
      fileSize: file.size,
      packageSha256,
    },
    { timeout: UPLOAD_TIMEOUT_MS }
  )
}

export async function uploadVersionPackage(file: File) {
  try {
    if (file.size > MULTIPART_UPLOAD_THRESHOLD) {
      return await uploadVersionPackageChunked(file)
    }
    return await uploadVersionPackageSingle(file)
  } catch (e) {
    const err = e as {
      message?: string
      code?: string
      response?: { data?: { message?: string }; status?: number }
    }
    const sizeMb = Math.round((file.size / (1024 * 1024)) * 10) / 10
    if (err.code === 'ECONNABORTED' || err.message?.toLowerCase().includes('timeout')) {
      throw new Error(
        `上传超时（安装包约 ${sizeMb}MB）。请检查网络与 OSS 连通性后重试；慢速网络下 220MB 可能需要数分钟。`
      )
    }
    if (err.message?.includes('CORS') || err.code === 'ERR_NETWORK') {
      throw new Error(
        `上传失败（安装包约 ${sizeMb}MB）。分片会直连 8080，请确认 linkx-server 已启动且 CORS 已生效。`
      )
    }
    throw new Error(err.response?.data?.message || err.message || 'upload failed')
  }
}
