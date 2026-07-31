import { get } from './request'

export interface ChartSeries {
  name: string
  key: string
  data: number[]
}

export interface TrendData {
  labels: string[]
  series: ChartSeries[]
}

export interface BreakdownItem {
  key: string
  name: string
  value: number
}

export interface StatisticOverview {
  totalUsers: number
  activeUsers: number
  onlineDevices: number
  pendingFeedback: number
  pendingReviews: number
  riskEvents: number
  todayNewUsers: number
  todayMessages: number
  todayLogins: number
  totalMessages: number
  totalUploads: number
  closedFeedback: number
}

export interface StatisticUsers {
  trend: TrendData
  statusBreakdown: BreakdownItem[]
  newUsersInRange: number
  loginSuccessInRange: number
  loginFailInRange: number
}

export interface StatisticContent {
  trend: TrendData
  messagesInRange: number
  momentsInRange: number
  uploadsInRange: number
}

export interface StatisticRisk {
  trend: TrendData
  reviewStatusBreakdown: BreakdownItem[]
  sensitiveHitsInRange: number
  messageStormsInRange: number
  pendingReviews: number
}

export interface StatisticFeedback {
  trend: TrendData
  statusBreakdown: BreakdownItem[]
  createdInRange: number
  repliedInRange: number
  closedInRange: number
}

export function fetchStatisticOverview(days = 14) {
  return get<StatisticOverview>('/admin/statistics/overview', { days })
}

export function fetchStatisticUsers(days = 14) {
  return get<StatisticUsers>('/admin/statistics/users', { days })
}

export function fetchStatisticContent(days = 14) {
  return get<StatisticContent>('/admin/statistics/content', { days })
}

export function fetchStatisticRisk(days = 14) {
  return get<StatisticRisk>('/admin/statistics/risk', { days })
}

export function fetchStatisticFeedback(days = 14) {
  return get<StatisticFeedback>('/admin/statistics/feedback', { days })
}
