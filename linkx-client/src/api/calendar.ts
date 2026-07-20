/**
 * 日历 API
 * 路径参数中的事件 ID 全程使用字符串，避免雪花 ID 经 Number 后精度丢失。
 */
import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface CalendarEventVO {
  id: string
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
export function getEvent(eventId: string) {
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
export function updateEvent(eventId: string, payload: SaveCalendarEventPayload) {
  return apiClient.put<never, ApiResult<CalendarEventVO>>(`/calendar/${eventId}`, payload)
}

/**
 * 删除事件
 */
export function deleteEvent(eventId: string) {
  return apiClient.delete<never, ApiResult<null>>(`/calendar/${eventId}`)
}

/**
 * 触发日程提醒：服务端写入消息通知列表
 */
export function fireReminder(eventId: string) {
  return apiClient.post<never, ApiResult<null>>(`/calendar/${eventId}/remind`)
}
