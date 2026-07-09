/**
 * 收藏 Store
 * 管理用户收藏的消息、笔记、链接等内容列表
 */

// 从 Pinia 导入 defineStore
import { defineStore } from 'pinia'
// 导入收藏项类型
import type { FavoriteItem } from '../types'
// 导入 mock 初始收藏数据
import { favorites as initialFavorites } from '../data/mockData'

/** 生成新收藏项的默认时间标签 */
function nowLabel(): string {
  return '今天'
}

// 定义并导出 favorites Store
export const useFavoritesStore = defineStore('favorites', {
  // 初始状态
  state: () => ({
    items: [...initialFavorites] as FavoriteItem[] // 收藏列表
  }),

  actions: {
    /**
     * 新增收藏项（插入列表头部）
     * @param item 收藏内容，id 与 time 可省略由 Store 自动生成
     */
    add(item: Omit<FavoriteItem, 'id' | 'time'> & { id?: string; time?: string }) {
      this.items.unshift({
        id: item.id ?? `fav-${Date.now()}`, // 缺省 id 基于时间戳
        time: item.time ?? nowLabel(),      // 缺省时间为「今天」
        title: item.title,
        preview: item.preview,
        type: item.type
      })
    },

    /**
     * 按 id 删除收藏
     * @param id 收藏项 id
     */
    remove(id: string) {
      this.items = this.items.filter(i => i.id !== id)
    },

    /**
     * 部分更新收藏项
     * @param id 收藏项 id
     * @param patch 要合并的字段
     */
    update(id: string, patch: Partial<Pick<FavoriteItem, 'title' | 'preview' | 'type'>>) {
      const item = this.items.find(i => i.id === id)
      if (item) Object.assign(item, patch) // 原地合并 patch
    }
  },

  // 持久化收藏列表
  persist: {
    key: 'linkx-favorites',
    paths: ['items']
  }
})
