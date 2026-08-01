import { del, get, post, put } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export interface AdminRole {
  id: number
  roleCode: string
  roleName: string
  description?: string
  dataScope?: number
  deptIds?: number[]
  status?: number
  createTime?: string
  updateTime?: string
}

export interface RolePayload {
  roleCode: string
  roleName: string
  description?: string
  dataScope?: number
  deptIds?: number[]
  status?: number
}

export interface RoleUser {
  id: string
  username: string
  nickname?: string
  status?: number
}

export function listRoles(params: PageQuery) {
  return get<PageResult<AdminRole>>('/admin/roles', params as Record<string, unknown>)
}

export function getRole(id: number) {
  return get<AdminRole>(`/admin/roles/${id}`)
}

export function createRole(body: RolePayload) {
  return post<number>('/admin/roles', body)
}

export function updateRole(id: number, body: RolePayload) {
  return put<null>(`/admin/roles/${id}`, body)
}

export function deleteRole(id: number) {
  return del<null>(`/admin/roles/${id}`)
}

export function getRoleMenus(id: number) {
  return get<number[]>(`/admin/roles/${id}/menus`)
}

export function assignRoleMenus(id: number, menuIds: number[]) {
  return put<null>(`/admin/roles/${id}/menus`, { menuIds })
}

export function getRolePermissions(id: number) {
  return get<number[]>(`/admin/roles/${id}/permissions`)
}

export function assignRolePermissions(id: number, permissionIds: number[]) {
  return put<null>(`/admin/roles/${id}/permissions`, { permissionIds })
}

export function getRoleUsers(id: number) {
  return get<RoleUser[]>(`/admin/roles/${id}/users`)
}

export function assignRoleUsers(id: number, userIds: string[]) {
  return put<null>(`/admin/roles/${id}/users`, { userIds })
}
