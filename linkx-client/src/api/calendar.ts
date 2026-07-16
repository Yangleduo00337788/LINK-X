/**
 * 日历 API
 */
import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface CalendarEventVO {
  id: number
  title: string
  date: string
  time?: string
  color?: string
  createTime?: string
  updateTime?: string
}

export interface SaveCalendarEventPayload {
  title: string
  date: string
  time?: string
  color?: string
}

/**
 * 获取所有日历事件
 */
export function listEvents() {
  return apiClient.get<never, ApiResult<CalendarEventVO[]>>('/calendar')
}

/**
 * 获取指定日期的事件
 */
export function listEventsByDate(date: string) {
  return apiClient.get<never, ApiResult<CalendarEventVO[]>>(`/calendar/date/${date}`)
}

/**
 * 获取单条事件
 */
export function getEvent(eventId: number) {
  return apiClient.get<never, ApiResult<CalendarEventVO>>(`/calendar/${eventId}`)
}

/**
 * 创建事件
 */
export function createEvent(payload: SaveCalendarEventPayload) {
  return apiClient.post<never, ApiResult<CalendarEventVO>>('/calendar', payload)
}

/**
 * 更新事件
 */
export function updateEvent(eventId: number, payload: SaveCalendarEventPayload) {
  return apiClient.put<never, ApiResult<CalendarEventVO>>(`/calendar/${eventId}`, payload)
}

/**
 * 删除事件
 */
export function deleteEvent(eventId: number) {
  return apiClient.delete<never, ApiResult<null>>(`/calendar/${eventId}`)
}
