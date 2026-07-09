import { defineStore } from 'pinia'
import type { AppItem, FavoriteItem } from '../types'
import type { LocalFileItem } from '../stores/files'

export const useSecondaryViewStore = defineStore('secondaryView', {
  state: () => ({
    activeApp: null as AppItem | null,
    activeFavorite: null as FavoriteItem | null,
    activeFile: null as LocalFileItem | null
  })
})
