/**
 * 作者：yangleduo
 */
import { get } from './request'
import type { TrendData } from './statistics'
import type { SnailJobTaskItem } from './scheduledTasks'
import type { SystemConnectionPool } from './systemMonitor'

export interface MonitorTrend {
  labels?: string[]
  series?: { key: string; name: string; data: number[] }[]
}

export interface MonitorNamedValue {
  key: string
  name: string
  value: number
}

export interface MonitorCache {
  refreshedAt?: string
  usedMemoryBytes: number
  maxMemoryBytes: number
  memoryUsagePercent: number
  connectedClients: number
  hitRatePercent: number
  qps: number
  redisVersion?: string
  info?: Record<string, string>
  memoryTrend?: MonitorTrend
  qpsTrend?: MonitorTrend
  hitRateTrend?: MonitorTrend
  connectionsTrend?: MonitorTrend
}

export interface MonitorService {
  refreshedAt?: string
  osName?: string
  osArch?: string
  hostName?: string
  availableProcessors?: number
  systemCpuLoadPercent: number
  processCpuLoadPercent: number
  systemTotalMemoryBytes: number
  systemFreeMemoryBytes: number
  systemMemoryUsagePercent: number
  jvmHeapUsedBytes: number
  jvmHeapMaxBytes: number
  jvmHeapUsagePercent: number
  jvmNonHeapUsedBytes?: number
  threadCount?: number
  peakThreadCount?: number
  gcCount?: number
  uptimeMs?: number
  startTime?: string
  javaVersion?: string
  diskTotalBytes: number
  diskFreeBytes: number
  diskUsagePercent: number
  diskPath?: string
  cpuTrend?: MonitorTrend
  memoryTrend?: MonitorTrend
}

export interface MonitorApiStats {
  refreshedAt?: string
  totalRequests: number
  successRequests: number
  failedRequests: number
  methodDistribution: MonitorNamedValue[]
  topPaths: MonitorNamedValue[]
  dailyTrend?: MonitorTrend
}

export interface MonitorTaskStats {
  refreshedAt?: string
  monitorAvailable: boolean
  totalTasks: number
  registeredTasks: number
  enabledTasks: number
  failedTasks: number
  successBatches: number
  failedBatches: number
  successRatePercent: number
  statusDistribution: MonitorNamedValue[]
  dailyTrend?: MonitorTrend
  tasks: SnailJobTaskItem[]
}

export interface MonitorSqlStatement {
  digest?: string
  sampleSql?: string
  execCount: number
  avgLatencyMs: number
  totalLatencyMs: number
}

export interface MonitorSql {
  refreshedAt?: string
  connectionPool?: SystemConnectionPool | null
  activeConnections: number
  questionsTotal: number
  slowQueries: number
  topStatements: MonitorSqlStatement[]
  connectionTrend?: MonitorTrend
}

export function toTrendData(trend?: MonitorTrend | null): TrendData | null {
  if (!trend?.labels?.length || !trend.series?.length) return null
  return {
    labels: trend.labels,
    series: trend.series.map((s) => ({ key: s.key, name: s.name, data: s.data || [] })),
  }
}

/** 除最后一个实时点外无历史采样（全为 0） */
export function isSparseMonitorTrend(trend?: MonitorTrend | null): boolean {
  const series = trend?.series?.[0]?.data
  if (!series || series.length <= 1) return true
  const historical = series.slice(0, -1)
  return historical.every((n) => !Number(n))
}

export function fetchMonitorCache(hours = 24) {
  return get<MonitorCache>('/admin/system-monitor/cache', { hours })
}

export function fetchMonitorService(hours = 24) {
  return get<MonitorService>('/admin/system-monitor/service', { hours })
}

export function fetchMonitorApiStats(days = 14) {
  return get<MonitorApiStats>('/admin/system-monitor/api-stats', { days })
}

export function fetchMonitorTasks(days = 7) {
  return get<MonitorTaskStats>('/admin/system-monitor/tasks', { days })
}

export function fetchMonitorSql(hours = 24, limit = 20) {
  return get<MonitorSql>('/admin/system-monitor/sql', { hours, limit })
}

export function formatMonitorBytes(bytes?: number | null) {
  if (bytes == null || bytes < 0) return '-'
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let value = bytes
  let i = 0
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024
    i += 1
  }
  return `${value.toFixed(i === 0 ? 0 : 2)} ${units[i]}`
}
