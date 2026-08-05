import { get, post, put } from './request'

export interface MessageStormPolicy {
  userThreshold: number
  userWindowSeconds: number
  groupMinMembers: number
  groupLargeMembers: number
  groupMidPerMinute: number
  groupLargePerMinute: number
}

export interface ScoreThresholdPolicy {
  mediumMin: number
  highMin: number
  criticalMin: number
}

export interface RateLimitPolicy {
  loginPerMinute: number
  registerPerMinute: number
  searchPerMinute: number
  listPerMinute: number
  writePerMinute: number
  uploadPerMinute: number
}

export interface LoginLockPolicy {
  clientMaxAttempts: number
  clientLockMinutes: number
  adminMaxAttempts: number
  adminLockMinutes: number
}

export interface RiskPolicyOverview {
  messageStorm: MessageStormPolicy
  scoreThresholds: ScoreThresholdPolicy
  rateLimits: RateLimitPolicy
  loginLock: LoginLockPolicy
  sensitiveFilterEnabled?: boolean
}

export interface RiskPolicyUpdatePayload {
  messageStormUserThreshold?: number
  messageStormUserWindowSeconds?: number
  messageStormGroupMinMembers?: number
  messageStormGroupLargeMembers?: number
  messageStormGroupMidPerMinute?: number
  messageStormGroupLargePerMinute?: number
  scoreMediumMin?: number
  scoreHighMin?: number
  scoreCriticalMin?: number
  rateLimitLoginPerMinute?: number
  rateLimitRegisterPerMinute?: number
  rateLimitSearchPerMinute?: number
  rateLimitListPerMinute?: number
  rateLimitWritePerMinute?: number
  rateLimitUploadPerMinute?: number
}

export interface RiskPolicySimulatePayload {
  text?: string
  subjectUserId?: string
}

export interface MatchedWordDetail {
  word?: string
  action?: string
}

export interface RiskPolicySimulateResult {
  sensitiveFilterEnabled?: boolean
  blocked?: boolean
  filtered?: boolean
  alerted?: boolean
  filteredText?: string
  matchedWords?: string[]
  matchedDetails?: MatchedWordDetail[]
  riskScore?: number
  riskLevel?: string
  riskFactors?: string[]
  ruleScoreDelta?: number
  ruleBlocked?: boolean
  ruleAlerted?: boolean
  matchedRules?: Array<{
    ruleId?: string
    ruleName?: string
    scoreDelta?: number
    actionType?: string
  }>
}

export function fetchRiskPolicies() {
  return get<RiskPolicyOverview>('/admin/risk-policies')
}

export function updateRiskPolicies(payload: RiskPolicyUpdatePayload) {
  return put<RiskPolicyOverview>('/admin/risk-policies', payload)
}

export function simulateRiskPolicy(payload: RiskPolicySimulatePayload) {
  return post<RiskPolicySimulateResult>('/admin/risk-policies/simulate', payload)
}
