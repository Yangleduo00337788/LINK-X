import { downloadFile, get, post, put } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export interface AdminUserListItem {
  /** 雪花 ID，后端以字符串返回，避免 JS 精度丢失 */
  id: string
  username: string
  nickname?: string
  avatar?: string
  email?: string
  phone?: string
  status?: number
  roles?: string[]
  createTime?: string
  updateTime?: string
}

export interface AdminUserDetail extends AdminUserListItem {
  signature?: string
  gender?: string
  birthday?: number
  country?: string
  province?: string
  region?: string
  permissions?: string[]
}

export interface DeviceItem {
  id: string
  deviceName?: string
  deviceType?: string
  ip?: string
  userAgent?: string
  lastActive?: string
  current?: boolean
}

export function listUsers(params: PageQuery) {
  return get<PageResult<AdminUserListItem>>('/admin/users', params as Record<string, unknown>)
}

export function getUser(id: string) {
  return get<AdminUserDetail>(`/admin/users/${id}`)
}

export function updateUser(id: string, body: Partial<AdminUserDetail>) {
  return put<null>(`/admin/users/${id}`, body)
}

export function freezeUser(id: string, reason?: string) {
  return post<null>(`/admin/users/${id}/freeze`, { reason })
}

export function unfreezeUser(id: string) {
  return post<null>(`/admin/users/${id}/unfreeze`)
}

export function banUser(id: string, reason?: string) {
  return post<null>(`/admin/users/${id}/ban`, { reason })
}

export function unbanUser(id: string) {
  return post<null>(`/admin/users/${id}/unban`)
}

export function listUserDevices(id: string) {
  return get<DeviceItem[]>(`/admin/users/${id}/devices`)
}

export interface UserLoginItem {
  id: string
  userId?: string
  username?: string
  ip?: string
  userAgent?: string
  success?: number
  reason?: string
  createTime?: string
}

export function listUserLogins(id: string, params?: PageQuery) {
  return get<PageResult<UserLoginItem>>(`/admin/users/${id}/logins`, (params || {}) as Record<string, unknown>)
}

export function exportUsers(params: PageQuery) {
  return downloadFile('/admin/users/export', params as Record<string, unknown>, 'users.csv')
}

