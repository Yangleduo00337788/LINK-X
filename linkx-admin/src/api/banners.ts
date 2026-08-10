/**
 * 作者：yangleduo
 */
import { del, get, post, put } from './request'
import type { PageQuery, PageResult } from '@/types/api'

export type BannerPosition = 'home' | 'login'

export interface BannerItem {
  id: string
  title: string
  imageUrl: string
  /** 入库用对象 key；编辑提交时回传 */
  imageKey?: string
  linkUrl?: string | null
  position?: BannerPosition
  sortOrder?: number
  status?: string
  startAt?: string | null
  endAt?: string | null
  publishedAt?: string
  publishedBy?: string
  createdBy?: string
  updatedBy?: string
  createTime?: string
  updateTime?: string
}

export interface BannerPayload {
  title: string
  /** 上传接口返回的 objectKey */
  imageUrl: string
  linkUrl?: string | null
  position: BannerPosition
  sortOrder?: number
  startAt?: number | null
  endAt?: number | null
}

export interface BannerUploadResult {
  objectKey: string
  url: string
}

export interface BannerQuery extends PageQuery {
  bannerStatus?: string
  position?: BannerPosition | ''
}

export function listBanners(params: BannerQuery) {
  return get<PageResult<BannerItem>>('/admin/banners', params as Record<string, unknown>)
}

export function getBanner(id: string) {
  return get<BannerItem>(`/admin/banners/${id}`)
}

export function uploadBannerImage(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return post<BannerUploadResult>('/admin/banners/upload', formData)
}

export function createBanner(body: BannerPayload) {
  return post<BannerItem>('/admin/banners', body)
}

export function updateBanner(id: string, body: BannerPayload) {
  return put<BannerItem>(`/admin/banners/${id}`, body)
}

export function deleteBanner(id: string) {
  return del<null>(`/admin/banners/${id}`)
}

export function publishBanner(id: string) {
  return post<BannerItem>(`/admin/banners/${id}/publish`)
}

export function unpublishBanner(id: string) {
  return post<BannerItem>(`/admin/banners/${id}/unpublish`)
}

/** 已发布且在时效内的 Banner（管理端登录页/仪表盘展示，匿名可调） */
export function listPublishedBanners(position: BannerPosition) {
  return get<BannerItem[]>('/app/banners', { position })
}
