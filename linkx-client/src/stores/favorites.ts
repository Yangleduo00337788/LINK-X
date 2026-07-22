/**
 * 收藏 Store
 * 管理用户收藏的消息、笔记、链接等内容列表
 * 数据从后端 API 加载
 */

import { defineStore } from 'pinia'
import type { FavoriteItem } from '../types'
import * as favoriteApi from '../api/favorite'
import type { FavoriteVO } from '../api/favorite'
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
    // fallback: comma separated
  }
  return t
    .split(/[,，]/)
    .map(s => s.trim())
    .filter(Boolean)
}

function looksLikeUrl(s: string): boolean {
  return /^https?:\/\//i.test(s.trim()) || s.trim().startsWith('data:image')
}

function mapVo(f: FavoriteVO): FavoriteItem {
  const content = f.content || ''
  const type = mapFavoriteType(f.type)
  let coverUrl: string | undefined
  if ((type === 'image' || type === 'link') && looksLikeUrl(content)) {
    const url = normalizeMediaUrl(content.split(/\s|\n/)[0])
    if (url && isDisplayableMediaUrl(url)) coverUrl = url
  }
  let createTimeMs: number | undefined
  if (f.createTime) {
    const parsed = Date.parse(f.createTime.replace(/-/g, '/'))
    if (Number.isFinite(parsed)) createTimeMs = parsed
  }
  return {
    id: String(f.id),
    title: f.title || '无标题',
    preview: content.slice(0, 120),
    content,
    type,
    tags: parseTags(f.tags),
    fileSize: f.fileSize != null ? Number(f.fileSize) : undefined,
    coverUrl,
    createTimeMs,
    time: f.createTime || f.updateTime || nowLabel()
  }
}

export const useFavoritesStore = defineStore('favorites', {
  state: () => ({
    items: [] as FavoriteItem[],
    loading: false,
    initialized: false
  }),

  getters: {
    usedBytes(state): number {
      return state.items.reduce((sum, i) => sum + (i.fileSize || 0), 0)
    }
  },

  actions: {
    async fetchFavorites() {
      if (this.loading) return
      this.loading = true
      try {
        const res = await favoriteApi.listFavorites()
        if (res.code === 200 && res.data) {
          this.items = res.data.map(mapVo)
          this.initialized = true
        }
      } catch (e) {
        console.error('加载收藏列表失败:', e)
      } finally {
        this.loading = false
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
          return true
        }
      } catch (e) {
        console.error('更新收藏失败:', e)
      }
      return false
    }
  },

  persist: false
})
