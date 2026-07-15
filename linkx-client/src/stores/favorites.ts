/**
 * 收藏 Store
 * 管理用户收藏的消息、笔记、链接等内容列表
 * 数据从后端 API 加载
 */

import { defineStore } from 'pinia'
import type { FavoriteItem } from '../types'
import * as favoriteApi from '../api/favorite'

/** 生成新收藏项的默认时间标签 */
function nowLabel(): string {
  return '今天'
}

// 定义并导出 favorites Store
export const useFavoritesStore = defineStore('favorites', {
  state: () => ({
    items: [] as FavoriteItem[],    // 收藏列表（从后端加载）
    loading: false,
    initialized: false             // 是否已从后端加载
  }),

  actions: {
    /**
     * 从后端加载收藏列表
     */
    async fetchFavorites() {
      if (this.loading) return
      this.loading = true
      try {
        const res = await favoriteApi.listFavorites()
        if (res.code === 200 && res.data) {
          this.items = res.data.map(f => ({
            id: String(f.id),
            title: f.title || '无标题',
            preview: f.content.slice(0, 100),
            type: 'note' as const,
            time: f.createTime ? new Date(f.createTime).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }) : nowLabel()
          }))
          this.initialized = true
        }
      } catch (e) {
        console.error('加载收藏列表失败:', e)
      } finally {
        this.loading = false
      }
    },

    /**
     * 新增收藏项
     */
    async add(item: Omit<FavoriteItem, 'id' | 'time'> & { id?: string; time?: string }) {
      try {
        const res = await favoriteApi.addFavorite({
          title: item.title,
          content: item.preview || item.title
        })
        if (res.code === 200 && res.data) {
          this.items.unshift({
            id: String(res.data.id),
            title: res.data.title || item.title,
            preview: item.preview || item.title,
            type: item.type,
            time: nowLabel()
          })
          return true
        }
      } catch (e) {
        console.error('添加收藏失败:', e)
      }
      return false
    },

    /**
     * 按 id 删除收藏
     */
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

    /**
     * 部分更新收藏项
     */
    async update(id: string, patch: Partial<Pick<FavoriteItem, 'title' | 'preview' | 'type'>>) {
      try {
        const res = await favoriteApi.updateFavorite(id, {
          title: patch.title,
          content: patch.preview
        })
        if (res.code === 200 && res.data) {
          const item = this.items.find(i => i.id === id)
          if (item) Object.assign(item, patch)
          return true
        }
      } catch (e) {
        console.error('更新收藏失败:', e)
      }
      return false
    }
  },

  persist: false // 改为从后端加载，不再持久化
})
