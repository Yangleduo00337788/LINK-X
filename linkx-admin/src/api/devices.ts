import { runAsyncExport } from './exportJobs'
import { get, post } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export interface AdminDeviceItem {
  id: string
  userId?: string
  username?: string
  nickname?: string
  deviceId: string
  deviceName?: string
  deviceType?: string
  ip?: string
  userAgent?: string
  lastActive?: string
  createTime?: string
  online?: boolean
  banned?: boolean
}

export interface DeviceQuery extends PageQuery {
  deviceType?: string
  userId?: string
}

export function listDevices(params: DeviceQuery) {
  return get<PageResult<AdminDeviceItem>>('/admin/devices', params as Record<string, unknown>)
}

export function kickDevice(userId: string, deviceId: string) {
  return post<null>(`/admin/devices/${userId}/${encodeURIComponent(deviceId)}/kick`)
}

export function banDevice(userId: string, deviceId: string, reason?: string) {
  return post<null>(`/admin/devices/${userId}/${encodeURIComponent(deviceId)}/ban`, { reason })
}

export function unbanDevice(userId: string, deviceId: string) {
  return post<null>(`/admin/devices/${userId}/${encodeURIComponent(deviceId)}/unban`)
}

export function exportDevices(params: DeviceQuery) {
  return runAsyncExport('devices', params as Record<string, unknown>)
}
