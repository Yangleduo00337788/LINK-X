import { ref } from 'vue'
import type { ChannelItem, AppItem, FavoriteItem } from '../types'

const activeChannel = ref<ChannelItem | null>(null)
const activeApp = ref<AppItem | null>(null)
const activeFavorite = ref<FavoriteItem | null>(null)
const menuOpen = ref(false)

export function useSecondaryView() {
  return {
    activeChannel,
    activeApp,
    activeFavorite,
    menuOpen
  }
}