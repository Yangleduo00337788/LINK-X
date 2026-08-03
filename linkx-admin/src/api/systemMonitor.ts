import { get } from './request'

export interface SystemRuntime {
  uptimeMs: number
  startTime?: string
  javaVersion?: string
  osName?: string
  osArch?: string
  availableProcessors?: number
  heapUsedBytes: number
  heapMaxBytes: number
  heapUsagePercent: number
  nonHeapUsedBytes?: number
  threadCount?: number
  peakThreadCount?: number
  gcCount?: number
}

export interface SystemDependency {
  name: string
  status: string
  latencyMs?: number
  details?: Record<string, unknown>
}

export interface SystemConnectionPool {
  poolName?: string
  activeConnections?: number
  idleConnections?: number
  totalConnections?: number
  maxConnections?: number
  threadsAwaitingConnection?: number
}

export interface SystemHttpMetrics {
  totalRequests: number
  clientErrorRequests: number
  serverErrorRequests: number
  avgLatencyMs: number
  p95LatencyMs: number
}

export interface SystemBusinessMetrics {
  loginSuccess: number
  loginFailure: number
  registerSuccess: number
  registerFailure: number
  messageSent: number
  fileUploadSuccess: number
  fileUploadFailure: number
  tokenRefreshSuccess: number
  tokenRefreshFailure: number
}

export interface SystemScheduledTaskSummary {
  monitorAvailable: boolean
  totalTasks: number
  registeredTasks: number
  enabledTasks: number
  failedTasks: number
}

export interface SystemStorageSummary {
  tableCount: number
  approximateRowCount: number
  dataBytes: number
  indexBytes: number
  totalBytes: number
}

export interface SystemTableStat {
  tableName: string
  engine?: string
  rowCount: number
  dataBytes: number
  indexBytes: number
  totalBytes: number
  tableComment?: string
  createTime?: string
  updateTime?: string
}

export interface SystemMonitorOverview {
  refreshedAt?: string
  applicationName?: string
  activeProfile?: string
  schemaName?: string
  runtime: SystemRuntime
  dependencies: SystemDependency[]
  connectionPool?: SystemConnectionPool | null
  http: SystemHttpMetrics
  business: SystemBusinessMetrics
  scheduledTasks: SystemScheduledTaskSummary
  storage?: SystemStorageSummary | null
  tables?: SystemTableStat[]
  rowCountApproximate?: boolean
}

export interface SystemTableStats {
  refreshedAt?: string
  schemaName?: string
  storage: SystemStorageSummary
  tables: SystemTableStat[]
  rowCountApproximate?: boolean
  cached?: boolean
}

export function fetchSystemMonitorOverview() {
  return get<SystemMonitorOverview>('/admin/system-monitor')
}

export function fetchSystemMonitorTables(refresh = false) {
  return get<SystemTableStats>('/admin/system-monitor/tables', { refresh })
}
