import { defineStore } from 'pinia'
import type { OverlayPage, AppItem } from '../types'

export interface FilePreviewPayload {
  fileName: string
  fileUrl?: string
  fileSize?: string
  isImage?: boolean
}

export const useOverlayStore = defineStore('overlay', {
  state: () => ({
    stack: [] as OverlayPage[],
    overlayApp: null as AppItem | null,
    filePreview: null as FilePreviewPayload | null
  }),

  getters: {
    currentPage(state): OverlayPage | null {
      return state.stack[state.stack.length - 1] ?? null
    },
    isOpen(state): boolean {
      return state.stack.length > 0
    },
    overlayFileName(state): string {
      return state.filePreview?.fileName ?? ''
    }
  },

  actions: {
    open(
      page: OverlayPage,
      payload?: { app?: AppItem; fileName?: string; filePreview?: FilePreviewPayload }
    ) {
      if (page === 'app-runner' && payload?.app) {
        this.overlayApp = payload.app
      }
      if (page === 'file-preview') {
        this.filePreview =
          payload?.filePreview ??
          (payload?.fileName ? { fileName: payload.fileName } : null)
      }
      if (this.stack[this.stack.length - 1] !== page) {
        this.stack.push(page)
      }
    },

    close() {
      const top = this.stack.pop()
      if (top === 'app-runner') this.overlayApp = null
      if (top === 'file-preview') this.filePreview = null
    },

    closeAll() {
      this.stack = []
      this.overlayApp = null
      this.filePreview = null
    }
  }
})
