import { del, get, post, put } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export interface RiskRuleItem {
  id: string
  name: string
  scope?: string
  keyword?: string
  conditionJson?: string
  scoreDelta?: number
  actionType?: string
  actionConfig?: string
  priority?: number
  enabled?: boolean
  createTime?: string
  updateTime?: string
}

export interface RiskRulePayload {
  name: string
  scope?: string
  keyword?: string
  conditionJson?: string
  scoreDelta?: number
  actionType?: string
  actionConfig?: string
  priority?: number
  enabled?: boolean
}

export interface RiskRuleSimulatePayload {
  scope?: string
  text?: string
  subjectUserId?: string
  messageCount?: number
  memberCount?: number
  taskRiskLevel?: string
  sensitiveBlocked?: boolean
  sensitiveAlerted?: boolean
  sensitiveFiltered?: boolean
  escalationCount?: number
}

export interface RiskRuleSimulateResult {
  scoreDelta?: number
  blocked?: boolean
  alerted?: boolean
  factors?: string[]
  matchedRules?: Array<{
    ruleId?: string
    ruleName?: string
    scoreDelta?: number
    actionType?: string
  }>
}

export function listRiskRules(params: PageQuery) {
  return get<PageResult<RiskRuleItem>>('/admin/risk-rules', params as Record<string, unknown>)
}

export function createRiskRule(body: RiskRulePayload) {
  return post<RiskRuleItem>('/admin/risk-rules', body)
}

export function updateRiskRule(id: string, body: RiskRulePayload) {
  return put<RiskRuleItem>(`/admin/risk-rules/${id}`, body)
}

export function deleteRiskRule(id: string) {
  return del<null>(`/admin/risk-rules/${id}`)
}

export function simulateRiskRules(body: RiskRuleSimulatePayload) {
  return post<RiskRuleSimulateResult>('/admin/risk-rules/simulate', body)
}
