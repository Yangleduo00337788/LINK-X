/**
 * 作者：yangleduo
 */
import { del, get, post, put } from './request'
import request from './request'
import type { PageQuery, PageResult } from '@/types/api'
import { tGlobal } from '@/i18n'

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

export interface VersionUploadCapability {
  directMultipart: boolean
  provider: string
  chunkSize: number
  maxConcurrency: number
}

export type UploadProgressCallback = (percent: number, detail?: string) => void

/** 超过此大小走分片上传 */
const MULTIPART_UPLOAD_THRESHOLD = 20 * 1024 * 1024
/** 经后端中转时的分片大小（与直传默认一致） */
const PROXY_CHUNK_SIZE = 10 * 1024 * 1024
/** 单片/整包上传超时（30 分钟） */
const UPLOAD_TIMEOUT_MS = 30 * 60 * 1000
/** 经后端中转分片并发 */
const PROXY_PART_UPLOAD_CONCURRENCY = 10

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

export function getVersionUploadCapability() {
  return get<VersionUploadCapability>('/admin/versions/upload/capability')
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
    throw new Error(data.message || tGlobal('version.packageUploadFail'))
  }
  return data.data
}

async function uploadInstallerPartProxy(
  file: File,
  uploadId: string,
  objectKey: string,
  partNumber: number,
  totalParts: number,
  chunkSize: number
) {
  const start = (partNumber - 1) * chunkSize
  const chunk = file.slice(start, Math.min(start + chunkSize, file.size))
  const formData = new FormData()
  formData.append('file', chunk, file.name)
  formData.append('uploadId', uploadId)
  formData.append('objectKey', objectKey)
  formData.append('partNumber', String(partNumber))
  const data = await postMultipart('/admin/versions/upload/multipart/part', formData, { direct: true })
  if (data.code !== 200) {
    throw new Error(data.message || tGlobal('version.uploadPartFailed', { part: partNumber, total: totalParts }))
  }
}

async function uploadVersionPackageChunkedProxy(
  file: File,
  onProgress?: UploadProgressCallback
): Promise<VersionUploadResult> {
  const chunkSize = PROXY_CHUNK_SIZE
  const sha256Promise = sha256HexOfFile(file)
  onProgress?.(1, 'init')
  const initRes = await post<{ uploadId: string; objectKey: string }>(
    '/admin/versions/upload/multipart/init',
    { fileName: file.name },
    { timeout: UPLOAD_TIMEOUT_MS }
  )
  const { uploadId, objectKey } = initRes
  const totalParts = Math.ceil(file.size / chunkSize)
  let completed = 0
  let nextPart = 1
  const workers = Array.from({ length: PROXY_PART_UPLOAD_CONCURRENCY }, async () => {
    while (true) {
      const partNumber = nextPart
      nextPart += 1
      if (partNumber > totalParts) {
        return
      }
      await uploadInstallerPartProxy(file, uploadId, objectKey, partNumber, totalParts, chunkSize)
      completed += 1
      onProgress?.(Math.min(95, Math.round((completed / totalParts) * 90) + 5))
    }
  })
  await Promise.all(workers)
  const packageSha256 = await sha256Promise
  onProgress?.(98, 'merge')
  const result = await post<VersionUploadResult>(
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
  onProgress?.(100)
  return result
}

async function uploadDirectPart(
  file: File,
  url: string,
  partNumber: number,
  chunkSize: number
): Promise<{ partNumber: number; etag: string }> {
  const start = (partNumber - 1) * chunkSize
  const chunk = file.slice(start, Math.min(start + chunkSize, file.size))
  const response = await fetch(url, {
    method: 'PUT',
    body: chunk,
  })
  if (!response.ok) {
    const text = await response.text().catch(() => '')
    throw new Error(
      tGlobal('version.uploadPartFailed', { part: partNumber, total: 0 }) +
        (text ? `: ${text.slice(0, 120)}` : ` HTTP ${response.status}`)
    )
  }
  const etag = (response.headers.get('ETag') || response.headers.get('etag') || '').replace(/"/g, '')
  if (!etag) {
    throw new Error(tGlobal('version.uploadPartFailed', { part: partNumber, total: 0 }))
  }
  return { partNumber, etag }
}

async function uploadVersionPackageDirect(
  file: File,
  capability: VersionUploadCapability,
  onProgress?: UploadProgressCallback
): Promise<VersionUploadResult> {
  const chunkSize = capability.chunkSize || PROXY_CHUNK_SIZE
  const concurrency = capability.maxConcurrency || PROXY_PART_UPLOAD_CONCURRENCY
  const sha256Promise = sha256HexOfFile(file)

  onProgress?.(1, 'init')
  const initRes = await post<{
    uploadId: string
    objectKey: string
    chunkSize: number
    maxConcurrency: number
  }>('/admin/versions/upload/direct/init', { fileName: file.name }, { timeout: UPLOAD_TIMEOUT_MS })

  const totalParts = Math.ceil(file.size / chunkSize)
  onProgress?.(3, 'presign')
  const presignRes = await post<{
    chunkSize: number
    parts: Array<{ partNumber: number; url: string }>
  }>(
    '/admin/versions/upload/direct/presign-parts',
    {
      objectKey: initRes.objectKey,
      uploadId: initRes.uploadId,
      totalParts,
    },
    { timeout: UPLOAD_TIMEOUT_MS }
  )

  const urlByPart = new Map(presignRes.parts.map((p) => [p.partNumber, p.url]))
  const uploadedParts: Array<{ partNumber: number; etag: string }> = []
  let completed = 0
  let nextPart = 1

  const workers = Array.from({ length: concurrency }, async () => {
    while (true) {
      const partNumber = nextPart
      nextPart += 1
      if (partNumber > totalParts) {
        return
      }
      const url = urlByPart.get(partNumber)
      if (!url) {
        throw new Error(tGlobal('version.uploadPartFailed', { part: partNumber, total: totalParts }))
      }
      const part = await uploadDirectPart(file, url, partNumber, chunkSize)
      uploadedParts.push(part)
      completed += 1
      onProgress?.(Math.min(95, Math.round((completed / totalParts) * 90) + 5))
    }
  })
  await Promise.all(workers)

  uploadedParts.sort((a, b) => a.partNumber - b.partNumber)
  const packageSha256 = await sha256Promise
  onProgress?.(98, 'merge')
  const result = await post<VersionUploadResult>(
    '/admin/versions/upload/direct/complete',
    {
      uploadId: initRes.uploadId,
      objectKey: initRes.objectKey,
      fileName: file.name,
      fileSize: file.size,
      packageSha256,
      parts: uploadedParts,
    },
    { timeout: UPLOAD_TIMEOUT_MS }
  )
  onProgress?.(100)
  return result
}

export async function uploadVersionPackage(file: File, onProgress?: UploadProgressCallback) {
  try {
    if (file.size <= MULTIPART_UPLOAD_THRESHOLD) {
      onProgress?.(50)
      const result = await uploadVersionPackageSingle(file)
      onProgress?.(100)
      return result
    }

    let capability: VersionUploadCapability | null = null
    try {
      capability = await getVersionUploadCapability()
    } catch {
      capability = null
    }

    if (capability?.directMultipart) {
      try {
        return await uploadVersionPackageDirect(file, capability, onProgress)
      } catch (e) {
        const err = e as Error
        if (err.message?.includes('Failed to fetch') || err.message?.includes('CORS')) {
          throw new Error(tGlobal('version.uploadCorsFail'))
        }
        throw e
      }
    }

    return await uploadVersionPackageChunkedProxy(file, onProgress)
  } catch (e) {
    const err = e as {
      message?: string
      code?: string
      response?: { data?: { message?: string }; status?: number }
    }
    const sizeMb = Math.round((file.size / (1024 * 1024)) * 10) / 10
    if (err.code === 'ECONNABORTED' || err.message?.toLowerCase().includes('timeout')) {
      throw new Error(tGlobal('version.uploadTimeout', { sizeMb }))
    }
    if (err.message?.includes('CORS') || err.code === 'ERR_NETWORK') {
      throw new Error(tGlobal('version.uploadNetworkFail', { sizeMb }))
    }
    throw new Error(err.response?.data?.message || err.message || tGlobal('version.packageUploadFail'))
  }
}
