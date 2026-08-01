import { del, get, post, put } from './request'

export interface AdminDept {
  id: number
  parentId?: number
  name: string
  sortOrder?: number
  status?: number
  createTime?: string
  updateTime?: string
  children?: AdminDept[]
}

export interface DeptPayload {
  parentId?: number
  name: string
  sortOrder?: number
  status?: number
}

export function listDepts() {
  return get<AdminDept[]>('/admin/depts')
}

export function createDept(body: DeptPayload) {
  return post<number>('/admin/depts', body)
}

export function updateDept(id: number, body: DeptPayload) {
  return put<null>(`/admin/depts/${id}`, body)
}

export function deleteDept(id: number) {
  return del<null>(`/admin/depts/${id}`)
}
