/**
 * 作者：yangleduo
 */
import { del, get, post, put } from './request'
import type {
  AdminMenuPayload,
  AdminMenuReorderItem,
  AdminMenuTree,
  PageQuery,
  PageResult,
} from '@/types/api'

export interface AdminPermission {
  id: number
  permissionCode: string
  permissionName: string
  resourceType?: string
  resourcePath?: string
  description?: string
  status?: number
}

export interface PermissionPayload {
  permissionCode: string
  permissionName: string
  resourceType?: string
  resourcePath?: string
  description?: string
  status?: number
}

export function listMenus() {
  return get<AdminMenuTree[]>('/admin/menus')
}

export function createMenu(body: AdminMenuPayload) {
  return post<number>('/admin/menus', body)
}

export function updateMenu(id: number, body: AdminMenuPayload) {
  return put<null>(`/admin/menus/${id}`, body)
}

export function deleteMenu(id: number) {
  return del<null>(`/admin/menus/${id}`)
}

export function reorderMenus(items: AdminMenuReorderItem[]) {
  return post<null>('/admin/menus/reorder', { items })
}

export function listPermissions(params: PageQuery) {
  return get<PageResult<AdminPermission>>('/admin/permissions', params as Record<string, unknown>)
}

export function createPermission(body: PermissionPayload) {
  return post<number>('/admin/permissions', body)
}

export function updatePermission(id: number, body: PermissionPayload) {
  return put<null>(`/admin/permissions/${id}`, body)
}

export function deletePermission(id: number) {
  return del<null>(`/admin/permissions/${id}`)
}
