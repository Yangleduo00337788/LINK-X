import { downloadFile, get } from './request'

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
  reviewEfficiencyTrend?: TrendData
  reviewStatusBreakdown: BreakdownItem[]
  sensitiveHitsInRange: number
  messageStormsInRange: number
  loginLocksInRange?: number
  rateLimitsInRange?: number
  pendingReviews: number
  resolvedReviewsInRange?: number
  avgHandleMinutesInRange?: number | null
  pendingOver24h?: number
  pendingOver72h?: number
}

export interface StatisticFeedback {
  trend: TrendData
  statusBreakdown: BreakdownItem[]
  createdInRange: number
  repliedInRange: number
  closedInRange: number
}

export interface GroupActivityItem {
  id: number
  name?: string
  messageCount: number
  memberCount: number
  lastMessageTime?: string
}

export interface StatisticGroups {
  totalGroups: number
  activeGroupsInRange: number
  newGroupsInRange: number
  groupMessagesInRange: number
  trend: TrendData
  topGroups: GroupActivityItem[]
}

export type HeatmapMetric = 'logins' | 'messages'

export interface ActivityHeatmap {
  metric: HeatmapMetric | string
  days: number
  maxValue: number
  total: number
  /** [weekday(0=Mon..6=Sun), hour(0-23), count] */
  cells: number[][]
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

export function fetchStatisticGroups(days = 14) {
  return get<StatisticGroups>('/admin/statistics/groups', { days })
}

export function exportStatistics(days = 14) {
  return downloadFile('/admin/statistics/export', { days }, 'statistics.csv')
}

export function fetchActivityHeatmap(days = 30, metric: HeatmapMetric = 'logins') {
  return get<ActivityHeatmap>('/admin/statistics/activity-heatmap', { days, metric })
}
