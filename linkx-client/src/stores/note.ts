/**
 * 笔记 Store
 * 管理笔记列表与编辑器状态，对接后端 /notes API
 */

import { defineStore } from 'pinia'
import * as noteApi from '../api/note'
import { useFavoritesStore } from './favorites'

/** 后端笔记类型 */
export type NoteType = 'note' | 'image' | 'link' | 'file'

/** 笔记实体 */
export interface Note {
  id: string
  title: string
  content: string
  type: NoteType
  createTime: string
  updateTime: string
}

/** 笔记草稿（本地未保存的编辑状态） */
interface NoteDraft {
  title: string
  content: string
}

const NOTE_STORAGE_KEY = 'linkx-note-draft'
const NOTE_TYPE_STORAGE_KEY = 'linkx-note-type'

export const useNoteStore = defineStore('note', {
  state: () => ({
    notes: [] as Note[],             // 笔记列表
    currentNoteId: null as string | null, // 当前编辑的笔记 ID
    title: '',                       // 当前编辑的标题
    content: '',                     // 当前编辑的正文
    noteType: 'note' as NoteType,    // 当前笔记类型
    loading: false,                  // 加载状态
    saving: false,                   // 保存状态
    initialized: false               // 是否已从后端加载
  }),

  getters: {
    /** 当前笔记对象 */
    currentNote(state): Note | null {
      return state.notes.find(n => n.id === state.currentNoteId) ?? null
    },

    /** 是否为新建笔记 */
    isNewNote(state): boolean {
      return state.currentNoteId === null
    },

    /** 按更新时间排序的笔记列表 */
    sortedNotes(state): Note[] {
      return [...state.notes].sort((a, b) =>
        new Date(b.updateTime).getTime() - new Date(a.updateTime).getTime()
      )
    }
  },

  actions: {
    /** 从后端加载笔记列表 */
    async fetchNotes() {
      this.loading = true
      try {
        const res = await noteApi.listNotes()
        if (res.code === 200 && res.data) {
          this.notes = res.data.map(n => ({
            id: String(n.id),
            title: n.title || '无标题',
            content: n.content,
            type: 'note',
            createTime: n.createTime,
            updateTime: n.updateTime
          }))
          this.initialized = true
        }
      } catch (e) {
        console.error('加载笔记列表失败:', e)
      } finally {
        this.loading = false
      }
    },

    /** 创建新笔记 */
    async createNote(title: string, content: string): Promise<Note | null> {
      this.saving = true
      try {
        const res = await noteApi.createNote({ title, content })
        if (res.code === 200 && res.data) {
          const note: Note = {
            id: String(res.data.id),
            title: res.data.title || '无标题',
            content: res.data.content,
            type: 'note',
            createTime: res.data.createTime,
            updateTime: res.data.updateTime
          }
          this.notes.unshift(note)
          return note
        }
      } catch (e) {
        console.error('创建笔记失败:', e)
      } finally {
        this.saving = false
      }
      return null
    },

    /** 更新笔记 */
    async updateNote(noteId: string, title: string, content: string): Promise<boolean> {
      this.saving = true
      try {
        const res = await noteApi.updateNote(noteId, { title, content })
        if (res.code === 200 && res.data) {
          const idx = this.notes.findIndex(n => n.id === noteId)
          if (idx >= 0) {
            this.notes[idx] = {
              id: String(res.data.id),
              title: res.data.title || '无标题',
              content: res.data.content,
              type: 'note',
              createTime: res.data.createTime,
              updateTime: res.data.updateTime
            }
          }
          return true
        }
      } catch (e) {
        console.error('更新笔记失败:', e)
      } finally {
        this.saving = false
      }
      return false
    },

    /** 删除笔记 */
    async deleteNote(noteId: string): Promise<boolean> {
      try {
        const res = await noteApi.deleteNote(noteId)
        if (res.code === 200) {
          this.notes = this.notes.filter(n => n.id !== noteId)
          if (this.currentNoteId === noteId) {
            this.newNote()
          }
          return true
        }
      } catch (e) {
        console.error('删除笔记失败:', e)
      }
      return false
    },

    /** 打开笔记进行编辑 */
    openNote(note: Note) {
      this.currentNoteId = note.id
      this.title = note.title
      this.content = note.content
      this.noteType = note.type
      this.saveDraft()
    },

    /** 按 ID 从后端拉取并打开笔记 */
    async openNoteById(noteId: string) {
      try {
        const res = await noteApi.getNote(noteId)
        if (res.code === 200 && res.data) {
          const note: Note = {
            id: String(res.data.id),
            title: res.data.title || '无标题',
            content: res.data.content,
            type: (res.data.type as NoteType) || 'note',
            createTime: res.data.createTime,
            updateTime: res.data.updateTime
          }
          const idx = this.notes.findIndex(n => n.id === note.id)
          if (idx >= 0) {
            this.notes[idx] = note
          } else {
            this.notes.unshift(note)
          }
          this.openNote(note)
          return note
        }
      } catch (e) {
        console.error('加载笔记失败:', e)
      }
      return null
    },

    /** 新建空白笔记 */
    newNote() {
      this.currentNoteId = null
      this.title = ''
      this.content = ''
      this.noteType = 'note'
      this.clearDraft()
    },

    /** 保存当前编辑内容（自动保存） */
    async save() {
      const title = this.title.trim() || '无标题'
      const content = this.content

      if (this.currentNoteId) {
        await this.updateNote(this.currentNoteId, title, content)
      } else if (content.trim()) {
        const note = await this.createNote(title, content)
        if (note) {
          this.currentNoteId = note.id
        }
      }
      this.saveDraft()
      // 保存后刷新收藏列表（笔记和收藏共用后端 /notes 接口）
      const favoritesStore = useFavoritesStore()
      void favoritesStore.fetchFavorites()
    },

    /** 保存草稿到 localStorage（防止意外丢失） */
    saveDraft() {
      try {
        localStorage.setItem(NOTE_STORAGE_KEY, JSON.stringify({
          title: this.title,
          content: this.content
        }))
        localStorage.setItem(NOTE_TYPE_STORAGE_KEY, this.noteType)
      } catch { /* ignore */ }
    },

    /** 从 localStorage 恢复草稿 */
    loadDraft() {
      try {
        const raw = localStorage.getItem(NOTE_STORAGE_KEY)
        if (raw) {
          const data = JSON.parse(raw) as NoteDraft
          this.title = data.title ?? ''
          this.content = data.content ?? ''
        }
        const type = localStorage.getItem(NOTE_TYPE_STORAGE_KEY)
        if (type) {
          this.noteType = type as NoteType
        }
      } catch { /* ignore */ }
    },

    /** 清除草稿 */
    clearDraft() {
      localStorage.removeItem(NOTE_STORAGE_KEY)
      localStorage.removeItem(NOTE_TYPE_STORAGE_KEY)
    },

    /** 初始化：加载笔记列表并恢复草稿 */
    async init() {
      await this.fetchNotes()
      if (!this.currentNoteId) {
        this.loadDraft()
      }
    }
  },

  persist: {
    key: 'linkx-note',
    paths: ['notes', 'currentNoteId', 'title', 'content', 'noteType']
  }
})
