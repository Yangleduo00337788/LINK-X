import { del, get, post, put } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export interface ApprovalFlowStep {
  name: string
  nodeType: 'approve' | 'countersign' | 'cc'
  assigneeType: 'user' | 'role'
  assigneeId?: string
  assigneeIds?: string[]
}

export interface ApprovalFlowItem {
  id: string
  name: string
  bizType?: string
  description?: string
  stepsJson?: string
  enabled?: boolean
  autoStart?: boolean
  priority?: number
  createTime?: string
  updateTime?: string
}

export interface ApprovalFlowPayload {
  name: string
  bizType: string
  description?: string
  stepsJson: string
  enabled?: boolean
  autoStart?: boolean
  priority?: number
}

export function listApprovalFlows(params: PageQuery) {
  return get<PageResult<ApprovalFlowItem>>('/admin/approval-flows', params as Record<string, unknown>)
}

export function createApprovalFlow(body: ApprovalFlowPayload) {
  return post<ApprovalFlowItem>('/admin/approval-flows', body)
}

export function updateApprovalFlow(id: string, body: ApprovalFlowPayload) {
  return put<ApprovalFlowItem>(`/admin/approval-flows/${id}`, body)
}

export function deleteApprovalFlow(id: string) {
  return del<null>(`/admin/approval-flows/${id}`)
}

export function parseApprovalSteps(json?: string): ApprovalFlowStep[] {
  if (!json) return []
  try {
    const parsed = JSON.parse(json) as ApprovalFlowStep[]
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export function stringifyApprovalSteps(steps: ApprovalFlowStep[]) {
  return JSON.stringify(steps)
}
