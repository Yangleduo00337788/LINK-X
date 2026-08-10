/**
 * 作者：yangleduo
 */
import { del, get, post, put } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export interface SensitiveWordItem {
  id: string
  word: string
  category?: string
  action?: string
  replacement?: string
  enabled?: boolean
  createTime?: string
  updateTime?: string
}

export interface SensitiveWordPayload {
  word: string
  category?: string
  action: string
  replacement?: string
  enabled?: boolean
}

export function listSensitiveWords(params: PageQuery) {
  return get<PageResult<SensitiveWordItem>>(
    '/admin/sensitive-words',
    params as Record<string, unknown>
  )
}

export function createSensitiveWord(body: SensitiveWordPayload) {
  return post<SensitiveWordItem>('/admin/sensitive-words', body)
}

export function updateSensitiveWord(id: string, body: SensitiveWordPayload) {
  return put<SensitiveWordItem>(`/admin/sensitive-words/${id}`, body)
}

export function deleteSensitiveWord(id: string) {
  return del<null>(`/admin/sensitive-words/${id}`)
}
