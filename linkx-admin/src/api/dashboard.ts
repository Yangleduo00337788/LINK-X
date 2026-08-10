/**
 * 作者：yangleduo
 */
import { get } from './request'
import type { TrendData } from './statistics'

export interface DashboardSummary {
  totalUsers: number
  /** 兼容字段，等同 wau */
  activeUsers: number
  dau: number
  wau: number
  mau: number
  onlineDevices: number
  pendingFeedback: number
  overdueFeedback: number
  pendingReviews: number
  overdueReviews: number
  pendingReports: number
  todaySensitiveHits: number
  todayRiskBlocks: number
  riskEvents: number
}

export interface DashboardRealtime {
  onlineDevices: number
  todayNewUsers: number
  todayMessages: number
  todayLogins: number
  riskEvents24h: number
}

export interface PendingTask {
  type: string
  title: string
  count: number
  path: string
}

export function fetchDashboardSummary() {
  return get<DashboardSummary>('/admin/dashboard/summary')
}

export function fetchDashboardTrends(days = 14) {
  return get<TrendData>('/admin/dashboard/trends', { days })
}

export function fetchDashboardRealtime() {
  return get<DashboardRealtime>('/admin/dashboard/realtime')
}

export function fetchPendingTasks() {
  return get<PendingTask[]>('/admin/dashboard/pending-tasks')
}
