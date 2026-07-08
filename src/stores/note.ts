import { defineStore } from 'pinia'

const NOTE_STORAGE_KEY = 'linkx-note-draft'

export const useNoteStore = defineStore('note', {
  state: () => ({
    title: '',
    content: ''
  }),

  actions: {
    load() {
      try {
        const raw = localStorage.getItem(NOTE_STORAGE_KEY)
        if (!raw) return
        const data = JSON.parse(raw) as { title?: string; content?: string }
        this.title = data.title ?? ''
        this.content = data.content ?? ''
      } catch {
        /* ignore */
      }
    },

    save() {
      localStorage.setItem(
        NOTE_STORAGE_KEY,
        JSON.stringify({ title: this.title, content: this.content, updatedAt: Date.now() })
      )
    },

    clear() {
      this.title = ''
      this.content = ''
      localStorage.removeItem(NOTE_STORAGE_KEY)
    }
  }
})
