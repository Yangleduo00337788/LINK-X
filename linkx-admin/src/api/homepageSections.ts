import { get, put } from './request'

export interface HomepageSectionItem {
  id: string
  sectionType?: string
  sectionKey?: string
  title?: string
  enabled?: boolean
  sortOrder?: number
  publishedCount?: number
  managePath?: string
}

export interface HomepageSectionReorderPayload {
  items: Array<{ id: string; enabled?: boolean; sortOrder: number }>
}

export function listHomepageSections() {
  return get<HomepageSectionItem[]>('/admin/homepage-sections')
}

export function reorderHomepageSections(body: HomepageSectionReorderPayload) {
  return put<null>('/admin/homepage-sections/reorder', body)
}
