import { get, post } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export interface ApprovalInboxItem {
  recordId: string
  instanceId: string
  title?: string
  flowName?: string
  bizType?: string
  bizId?: string
  stepName?: string
  nodeType?: string
  status?: string
  applicantName?: string
  createTime?: string
}

export interface ApprovalTimelineItem {
  id: string
  stepIndex?: number
  stepName?: string
  nodeType?: string
  assigneeId?: string
  assigneeName?: string
  status?: string
  comment?: string
  actionTime?: string
}

export interface ApprovalInstance {
  id: string
  flowId?: string
  flowName?: string
  bizType?: string
  bizId?: string
  title?: string
  status?: string
  currentStep?: number
  applicantName?: string
  finishedAt?: string
  createTime?: string
  timeline?: ApprovalTimelineItem[]
}

export interface ApprovalStartPayload {
  flowId: string
  bizType: string
  bizId: string
  title: string
}

export function listApprovalInbox(params: PageQuery) {
  return get<PageResult<ApprovalInboxItem>>('/admin/approvals/inbox', params as Record<string, unknown>)
}

export function listApprovalCc(params: PageQuery) {
  return get<PageResult<ApprovalInboxItem>>('/admin/approvals/cc', params as Record<string, unknown>)
}

export function getApprovalInstance(id: string) {
  return get<ApprovalInstance>(`/admin/approvals/instances/${id}`)
}

export function startApproval(body: ApprovalStartPayload) {
  return post<ApprovalInstance>('/admin/approvals/start', body)
}

export function approveApprovalRecord(id: string, comment?: string) {
  return post<null>(`/admin/approvals/records/${id}/approve`, { comment })
}

export function rejectApprovalRecord(id: string, comment?: string) {
  return post<null>(`/admin/approvals/records/${id}/reject`, { comment })
}
