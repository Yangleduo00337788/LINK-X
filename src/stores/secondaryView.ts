import { defineStore } from 'pinia'
import type { AppItem, FavoriteItem } from '../types'

export const useSecondaryViewStore = defineStore('secondaryView', {
  state: () => ({
    activeApp: null as AppItem | null,
    activeFavorite: null as FavoriteItem | null
  })
})
