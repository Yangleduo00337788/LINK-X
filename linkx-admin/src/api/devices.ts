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
