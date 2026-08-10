/**
 * 作者：yangleduo
 */
import { del, get, post, put } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export type NoticeTargetSide = 'admin' | 'client'

export interface NoticeItem {
  id: string
  title: string
  content: string
  targetSide?: NoticeTargetSide
  status?: string
  publishedAt?: string
  publishedBy?: string
  createdBy?: string
  updatedBy?: string
  createTime?: string
  updateTime?: string
}

export interface NoticePayload {
  title: string
  content: string
  targetSide: NoticeTargetSide
}

export interface NoticeQuery extends PageQuery {
  noticeStatus?: string
  targetSide?: NoticeTargetSide | ''
}

export function listNotices(params: NoticeQuery) {
  return get<PageResult<NoticeItem>>('/admin/notices', params as Record<string, unknown>)
}

export function getNotice(id: string) {
  return get<NoticeItem>(`/admin/notices/${id}`)
}

export function createNotice(body: NoticePayload) {
  return post<NoticeItem>('/admin/notices', body)
}

export function updateNotice(id: string, body: NoticePayload) {
  return put<NoticeItem>(`/admin/notices/${id}`, body)
}

export function deleteNotice(id: string) {
  return del<null>(`/admin/notices/${id}`)
}

export function publishNotice(id: string) {
  return post<NoticeItem>(`/admin/notices/${id}/publish`)
}

export function unpublishNotice(id: string) {
  return post<NoticeItem>(`/admin/notices/${id}/unpublish`)
}

/** 管理端通知收件箱：已发布的管理端公告 */
export function listNoticeInbox(params: PageQuery & { keyword?: string }) {
  return get<PageResult<NoticeItem>>('/admin/notices/inbox', params as Record<string, unknown>)
}
