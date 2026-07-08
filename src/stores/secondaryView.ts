import { defineStore } from 'pinia'
import type { ChannelItem, AppItem, FavoriteItem } from '../types'

export const useSecondaryViewStore = defineStore('secondaryView', {
  state: () => ({
    activeChannel: null as ChannelItem | null,
    activeApp: null as AppItem | null,
    activeFavorite: null as FavoriteItem | null,
    menuOpen: false
  })
})
