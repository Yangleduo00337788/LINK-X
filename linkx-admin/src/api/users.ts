/**
 * 作者：yangleduo
 */
import { runAsyncExport } from './exportJobs'
import { get, post, put } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export interface AdminUserListItem {
  /** 雪花 ID，后端以字符串返回，避免 JS 精度丢失 */
  id: string
  username: string
  nickname?: string
  avatar?: string
  email?: string
  phone?: string
  deptId?: number
  deptName?: string
  status?: number
  roles?: string[]
  createTime?: string
  updateTime?: string
}

export interface UserUpdatePayload {
  nickname?: string
  email?: string
  phone?: string
  signature?: string
  /** 0 表示清除部门 */
  deptId?: number
}

export interface AdminUserDetail extends AdminUserListItem {
  signature?: string
  gender?: string
  birthday?: number
  country?: string
  province?: string
  region?: string
  permissions?: string[]
  deviceBindingEnabled?: boolean
}

export interface DeviceItem {
  id: string
  deviceName?: string
  deviceType?: string
  ip?: string
  userAgent?: string
  lastActive?: string
  current?: boolean
  online?: boolean
  banned?: boolean
  approved?: boolean
}

export interface UserListQuery extends PageQuery {
  deptId?: number
}

export function listUsers(params: UserListQuery) {
  return get<PageResult<AdminUserListItem>>('/admin/users', params as Record<string, unknown>)
}

export function getUser(id: string) {
  return get<AdminUserDetail>(`/admin/users/${id}`)
}

export function updateUser(id: string, body: UserUpdatePayload) {
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

export interface ResetPasswordResult {
  generated: boolean
  temporaryPassword?: string | null
}

export function resetUserPassword(id: string, newPassword?: string) {
  return post<ResetPasswordResult>(`/admin/users/${id}/reset-password`, {
    newPassword: newPassword || undefined,
  })
}

export function listUserDevices(id: string) {
  return get<DeviceItem[]>(`/admin/users/${id}/devices`)
}

export function setUserDeviceBinding(id: string, enabled: boolean) {
  return post<null>(`/admin/users/${id}/device-binding`, { enabled })
}

export function approveUserDevice(id: string, deviceId: string) {
  return post<null>(`/admin/users/${id}/devices/${encodeURIComponent(deviceId)}/approve`)
}

export function revokeUserDevice(id: string, deviceId: string) {
  return post<null>(`/admin/users/${id}/devices/${encodeURIComponent(deviceId)}/revoke`)
}

export interface UserLoginItem {
  id: string
  userId?: string
  username?: string
  ip?: string
  region?: string
  userAgent?: string
  success?: number
  reason?: string
  createTime?: string
}

export function listUserLogins(id: string, params?: PageQuery) {
  return get<PageResult<UserLoginItem>>(
    `/admin/users/${id}/logins`,
    (params || {}) as Record<string, unknown>
  )
}

export function exportUsers(params: UserListQuery) {
  return runAsyncExport('users', params as Record<string, unknown>)
}
