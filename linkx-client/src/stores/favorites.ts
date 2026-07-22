/**
 * 收藏 Store
 * 管理用户收藏列表、空间配额、标签库（对接 /favorites）
 */

import { defineStore } from 'pinia'
import type { FavoriteItem } from '../types'
import * as favoriteApi from '../api/favorite'
import type { FavoriteStorageVO, FavoriteTagVO, FavoriteVO } from '../api/favorite'
import { isDisplayableMediaUrl, normalizeMediaUrl } from '../utils/mediaUrl'

function nowLabel(): string {
  return '今天'
}

function mapFavoriteType(type?: string): FavoriteItem['type'] {
  if (type === 'image' || type === 'link' || type === 'file' || type === 'note' || type === 'message') {
    return type
  }
  if (type === 'other') return 'other'
  return 'note'
}

function parseTags(raw?: string | null): string[] {
  if (!raw) return []
  const t = raw.trim()
  if (!t) return []
  try {
    const parsed = JSON.parse(t)
    if (Array.isArray(parsed)) {
      return parsed.map(x => String(x).trim()).filter(Boolean)
    }
  } catch {
    // fallback
  }
  return t
    .split(/[,，]/)
    .map(s => s.trim())
    .filter(Boolean)
}

function looksLikeUrl(s: string): boolean {
  return /^https?:\/\//i.test(s.trim()) || s.trim().startsWith('data:image') || s.trim().startsWith('blob:')
}

function isImagePath(s: string): boolean {
  return /\.(png|jpe?g|gif|webp|bmp|svg)(\?|#|$)/i.test(s)
}

/**
 * 仅图片 / 文件生成封面；笔记、聊天记录、其它不展示图。
 * 文件无扩展名时仍尝试用 URL（加载失败由 UI @error 回退）。
 */
function resolveCoverUrl(type: FavoriteItem['type'], content: string, _title: string): string | undefined {
  if (type !== 'image' && type !== 'file' && type !== 'link') return undefined
  // data:image 可能含空白，不能按空白截断
  const raw = content.trim().startsWith('data:image')
    ? content.trim()
    : (content || '').trim().split(/\s|\n/)[0] || ''
  if (!raw || !looksLikeUrl(raw)) return undefined
  // 历史 bug：收藏时把 URL 截成约 80 字，几乎必然无法加载
  if (/^https?:\/\//i.test(raw) && raw.length < 96 && !isImagePath(raw) && !/[?&]X-Amz-Signature=/i.test(raw)) {
    return undefined
  }
  const url = normalizeMediaUrl(raw)
  if (!url || !isDisplayableMediaUrl(url)) return undefined
  if (type === 'image' || type === 'file') return url
  if (type === 'link' && isImagePath(url)) return url
  return undefined
}

function mapVo(f: FavoriteVO): FavoriteItem {
  const content = f.content || ''
  const type = mapFavoriteType(f.type)
  const coverUrl = resolveCoverUrl(type, content, f.title || '')
  let createTimeMs: number | undefined
  if (f.createTime) {
    const parsed = Date.parse(f.createTime.replace(/-/g, '/'))
    if (Number.isFinite(parsed)) createTimeMs = parsed
  }
  return {
    id: String(f.id),
    title: f.title || '无标题',
    preview: type === 'image' || type === 'file' ? (f.title || content.slice(0, 80)) : content.slice(0, 120),
    content,
    type,
    tags: parseTags(f.tags),
    fileSize: f.fileSize != null ? Number(f.fileSize) : undefined,
    coverUrl,
    createTimeMs,
    time: f.createTime || f.updateTime || nowLabel()
  }
}

const emptyTypeCounts = (): Record<string, number> => ({
  all: 0,
  link: 0,
  image: 0,
  file: 0,
  note: 0,
  message: 0,
  other: 0
})

export const useFavoritesStore = defineStore('favorites', {
  state: () => ({
    items: [] as FavoriteItem[],
    tags: [] as FavoriteTagVO[],
    storage: null as FavoriteStorageVO | null,
    typeCounts: emptyTypeCounts(),
    loading: false,
    initialized: false
  }),

  getters: {
    usedBytes(state): number {
      return state.storage?.usedBytes ?? 0
    },
    quotaBytes(state): number {
      return state.storage?.quotaBytes ?? 30 * 1024 * 1024 * 1024
    },
    usedPercent(state): number {
      return state.storage?.usedPercent ?? 0
    }
  },

  actions: {
    async refreshAll() {
      await Promise.all([this.fetchFavorites(), this.fetchStorage(), this.fetchTags()])
      this.initialized = true
    },

    async fetchFavorites() {
      if (this.loading) return
      this.loading = true
      try {
        const res = await favoriteApi.listFavorites()
        if (res.code === 200 && res.data) {
          this.items = res.data.map(mapVo)
        }
      } catch (e) {
        console.error('加载收藏列表失败:', e)
      } finally {
        this.loading = false
      }
    },

    async fetchStorage() {
      try {
        const res = await favoriteApi.getFavoriteStorage()
        if (res.code === 200 && res.data) {
          this.storage = res.data
          this.typeCounts = { ...emptyTypeCounts(), ...(res.data.typeCounts || {}) }
          if (this.typeCounts.all == null) {
            this.typeCounts.all = res.data.itemCount ?? this.items.length
          }
        }
      } catch (e) {
        console.error('加载收藏空间失败:', e)
      }
    },

    async fetchTags() {
      try {
        const res = await favoriteApi.listFavoriteTags()
        if (res.code === 200 && res.data) {
          this.tags = res.data
        }
      } catch (e) {
        console.error('加载收藏标签失败:', e)
      }
    },

    async add(item: Omit<FavoriteItem, 'id' | 'time'> & { id?: string; time?: string }) {
      try {
        const res = await favoriteApi.addFavorite({
          title: item.title,
          content: item.content || item.preview || item.title,
          type:
            item.type === 'link' ||
            item.type === 'image' ||
            item.type === 'file' ||
            item.type === 'note' ||
            item.type === 'message'
              ? item.type
              : 'note',
          tags: item.tags?.length ? JSON.stringify(item.tags) : undefined,
          fileSize: item.fileSize
        })
        if (res.code === 200 && res.data) {
          this.items.unshift(mapVo(res.data))
          await Promise.all([this.fetchStorage(), this.fetchTags()])
          return true
        }
      } catch (e) {
        console.error('添加收藏失败:', e)
      }
      return false
    },

    async remove(id: string) {
      try {
        const res = await favoriteApi.removeFavorite(id)
        if (res.code === 200) {
          this.items = this.items.filter(i => i.id !== id)
          await Promise.all([this.fetchStorage(), this.fetchTags()])
          return true
        }
      } catch (e) {
        console.error('删除收藏失败:', e)
      }
      return false
    },

    async update(
      id: string,
      patch: Partial<Pick<FavoriteItem, 'title' | 'preview' | 'content' | 'type' | 'tags' | 'fileSize'>>
    ) {
      try {
        const current = this.items.find(i => i.id === id)
        const res = await favoriteApi.updateFavorite(id, {
          title: patch.title,
          content: patch.content ?? patch.preview ?? current?.content ?? current?.preview,
          type:
            patch.type === 'link' ||
            patch.type === 'image' ||
            patch.type === 'file' ||
            patch.type === 'note' ||
            patch.type === 'message'
              ? patch.type
              : undefined,
          tags: patch.tags ? JSON.stringify(patch.tags) : undefined,
          fileSize: patch.fileSize
        })
        if (res.code === 200 && res.data) {
          const idx = this.items.findIndex(i => i.id === id)
          if (idx >= 0) this.items[idx] = mapVo(res.data)
          await this.fetchTags()
          if (patch.fileSize != null) await this.fetchStorage()
          return true
        }
      } catch (e) {
        console.error('更新收藏失败:', e)
      }
      return false
    },

    async createTag(name: string, color?: string) {
      const res = await favoriteApi.createFavoriteTag({ name, color })
      if (res.code !== 200 || !res.data) {
        throw new Error(res.message || '创建标签失败')
      }
      await this.fetchTags()
      return res.data
    },

    async deleteTag(tagId: string) {
      const res = await favoriteApi.deleteFavoriteTag(tagId)
      if (res.code !== 200) {
        throw new Error(res.message || '删除标签失败')
      }
      await this.fetchTags()
    }
  },

  persist: false
})
