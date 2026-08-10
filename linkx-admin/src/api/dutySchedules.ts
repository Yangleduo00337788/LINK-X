/**
 * 作者：yangleduo
 */
import { del, get, post, put } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export interface DutyScheduleSlot {
  id?: string
  weekday: number
  startTime: string
  endTime: string
  assigneeId: string
  assigneeName?: string
  sortOrder?: number
}

export interface DutyScheduleItem {
  id: string
  name: string
  description?: string
  timezone?: string
  enabled?: boolean
  slots?: DutyScheduleSlot[]
  createTime?: string
  updateTime?: string
}

export interface DutySchedulePayload {
  name: string
  description?: string
  timezone?: string
  enabled?: boolean
  slots?: DutyScheduleSlot[]
}

export function listDutySchedules(params: PageQuery) {
  return get<PageResult<DutyScheduleItem>>('/admin/duty-schedules', params as Record<string, unknown>)
}

export function getDutySchedule(id: string) {
  return get<DutyScheduleItem>(`/admin/duty-schedules/${id}`)
}

export function createDutySchedule(body: DutySchedulePayload) {
  return post<DutyScheduleItem>('/admin/duty-schedules', body)
}

export function updateDutySchedule(id: string, body: DutySchedulePayload) {
  return put<DutyScheduleItem>(`/admin/duty-schedules/${id}`, body)
}

export function deleteDutySchedule(id: string) {
  return del<null>(`/admin/duty-schedules/${id}`)
}
