import { defineStore } from 'pinia'
import type { OverlayPage, AppItem } from '../types'

export const useOverlayStore = defineStore('overlay', {
  state: () => ({
    stack: [] as OverlayPage[],
    overlayApp: null as AppItem | null,
    overlayFileName: ''
  }),

  getters: {
    currentPage(state): OverlayPage | null {
      return state.stack[state.stack.length - 1] ?? null
    },
    isOpen(state): boolean {
      return state.stack.length > 0
    }
  },

  actions: {
    open(page: OverlayPage, payload?: { app?: AppItem; fileName?: string }) {
      if (page === 'app-runner' && payload?.app) {
        this.overlayApp = payload.app
      }
      if (page === 'file-preview' && payload?.fileName) {
        this.overlayFileName = payload.fileName
      }
      if (this.stack[this.stack.length - 1] !== page) {
        this.stack.push(page)
      }
    },

    close() {
      const top = this.stack.pop()
      if (top === 'app-runner') this.overlayApp = null
      if (top === 'file-preview') this.overlayFileName = ''
    },

    closeAll() {
      this.stack = []
      this.overlayApp = null
      this.overlayFileName = ''
    }
  }
})
