import { get } from './request'
import type { AdminMenuTree, PageQuery, PageResult } from '@/types/api'

export interface AdminPermission {
  id: number
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

export function listPermissions(params: PageQuery) {
  return get<PageResult<AdminPermission>>('/admin/permissions', params as Record<string, unknown>)
}
