/**
 * 作者：yangleduo
 */
import { get, post } from './request'
import type { BreakdownItem, ChartSeries, TrendData } from './statistics'

export interface BiMetric {
  key: string
  name: string
  dimensions: string[]
  drillTarget?: BiDrillTarget
}

export interface BiDrillTarget {
  route: string
  query?: Record<string, string>
}

export interface BiQueryPayload {
  metric: string
  dimension?: string
  days?: number
  comparePrevious?: boolean
}

export interface BiQueryResult {
  metric: string
  dimension: string
  days: number
  labels: string[]
  series: ChartSeries[]
  compareSeries?: ChartSeries[]
  compareTotalDeltaPct?: number
  breakdown?: BreakdownItem[]
  drillTarget?: BiDrillTarget
}

export interface BigScreenData {
  refreshedAt?: string
  totalUsers: number
  dau: number
  onlineDevices: number
  pendingFeedback: number
  pendingReviews: number
  todayMessages: number
  todayLogins: number
  todayRiskEvents: number
  kpiTrends?: Record<string, number[]>
  tickers?: Array<{
    type?: string
    title?: string
    relatedId?: number
    ts?: number
  }>
}

export function listBiMetrics() {
  return get<BiMetric[]>('/admin/bi/metrics')
}

export function queryBi(body: BiQueryPayload) {
  return post<BiQueryResult>('/admin/bi/query', body)
}

export function fetchBigScreenData() {
  return get<BigScreenData>('/admin/big-screen/data')
}

export function toTrendData(result: BiQueryResult | null): TrendData | null {
  if (!result?.labels?.length) return null
  const series = [...(result.series || [])]
  if (result.compareSeries?.length) {
    series.push(...result.compareSeries)
  }
  return { labels: result.labels, series }
}
