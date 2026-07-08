import { defineStore } from 'pinia'
import type { FavoriteItem } from '../types'
import { favorites as initialFavorites } from '../data/mockData'

function nowLabel(): string {
  return '今天'
}

export const useFavoritesStore = defineStore('favorites', {
  state: () => ({
    items: [...initialFavorites] as FavoriteItem[]
  }),

  actions: {
    add(item: Omit<FavoriteItem, 'id' | 'time'> & { id?: string; time?: string }) {
      this.items.unshift({
        id: item.id ?? `fav-${Date.now()}`,
        time: item.time ?? nowLabel(),
        title: item.title,
        preview: item.preview,
        type: item.type
      })
    },

    remove(id: string) {
      this.items = this.items.filter(i => i.id !== id)
    },

    update(id: string, patch: Partial<Pick<FavoriteItem, 'title' | 'preview' | 'type'>>) {
      const item = this.items.find(i => i.id === id)
      if (item) Object.assign(item, patch)
    }
  },

  persist: {
    key: 'linkx-favorites',
    paths: ['items']
  }
})
