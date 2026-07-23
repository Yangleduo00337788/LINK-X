import { apiClient } from './client'
import type { ApiResult } from '../types/auth'

export interface UserDataExport {
  userId?: string | number
  username?: string
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
  exportTime?: string
  friends?: Array<Record<string, unknown>>
  conversations?: Array<Record<string, unknown>>
  recentMessages?: Array<Record<string, unknown>>
  devices?: Array<Record<string, unknown>>
  notes?: Array<Record<string, unknown>>
}

export function exportUserData() {
  return apiClient.get<never, ApiResult<UserDataExport>>('/compliance/export')
}

export function purgeUserData() {
  return apiClient.post<never, ApiResult<null>>('/compliance/purge')
}
