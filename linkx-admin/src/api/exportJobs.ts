import { downloadFile, get, post } from './request'

export type ExportModule =
  | 'users'
  | 'devices'
  | 'blacklist'
  | 'risk-events'
  | 'reviews'
  | 'feedback'
  | 'audit-logs'
  | 'login-logs'
  | 'statistics'

export type ExportJobStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'EXPIRED'

export type ExportJob = {
  id: string | number
  module: string
  status: ExportJobStatus
  rowCount?: number
  fileName?: string
  errorMessage?: string
  expireAt?: string
  createTime?: string
  updateTime?: string
}

export function createExportJob(module: ExportModule, query?: Record<string, unknown>) {
  return post<ExportJob>('/admin/export-jobs', { module, query: query || {} })
}

export function getExportJob(id: string | number) {
  return get<ExportJob>(`/admin/export-jobs/${id}`)
}

export function downloadExportJob(id: string | number, fallbackName = 'export.csv') {
  return downloadFile(`/admin/export-jobs/${id}/download`, undefined, fallbackName)
}

function sleep(ms: number) {
  return new Promise((r) => setTimeout(r, ms))
}

/**
 * 创建异步导出 → 轮询至完成 → 自动下载。
 * 保留与原先 sync export 相同的调用体验。
 */
export async function runAsyncExport(
  module: ExportModule,
  query?: Record<string, unknown>,
  options?: { timeoutMs?: number; intervalMs?: number }
) {
  const timeoutMs = options?.timeoutMs ?? 60_000
  const intervalMs = options?.intervalMs ?? 800
  const job = await createExportJob(module, query)
  const started = Date.now()
  let current = job

  while (current.status === 'PENDING' || current.status === 'RUNNING') {
    if (Date.now() - started > timeoutMs) {
      throw new Error('export timeout')
    }
    await sleep(intervalMs)
    current = await getExportJob(current.id)
  }

  if (current.status === 'FAILED') {
    throw new Error(current.errorMessage || 'export failed')
  }
  if (current.status !== 'SUCCESS') {
    throw new Error(`export ${current.status}`)
  }

  await downloadExportJob(current.id, current.fileName || `${module}.csv`)
  return current
}
