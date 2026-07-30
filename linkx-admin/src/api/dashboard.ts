import { get } from './request'

export interface DashboardSummary {
  totalUsers: number
  activeUsers: number
  onlineDevices: number
  pendingFeedback: number
  pendingReviews: number
  riskEvents: number
}

export function fetchDashboardSummary() {
  return get<DashboardSummary>('/admin/dashboard/summary')
}
