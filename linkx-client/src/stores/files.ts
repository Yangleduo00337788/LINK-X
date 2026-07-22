/**
 * 云端文件 Store（对接 /files）
 */

import { defineStore } from 'pinia'
import * as filesApi from '../api/files'
import { formatFileSize } from '../utils/file'

export interface LocalFileItem {
  id: string
  title: string
  size: string
  sizeBytes: number
  time: string
  timeFull: string
  type: 'document' | 'image' | 'media' | 'other'
  sender: string
  fileUrl?: string
  conversationId?: string
  conversationName?: string
  ext: string
}

function formatTime(ts?: number): string {
  if (!ts) return ''
  try {
    return new Date(ts).toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    }).replace(/\//g, '-')
  } catch {
    return ''
  }
}

function formatTimeFull(ts?: number): string {
  if (!ts) return ''
  try {
    const d = new Date(ts)
    const date = d
      .toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
      .replace(/\//g, '-')
    const time = d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
    return `${date} ${time}`
  } catch {
    return ''
  }
}

function extOf(name?: string): string {
  if (!name) return ''
  const i = name.lastIndexOf('.')
  return i >= 0 ? name.slice(i + 1).toLowerCase() : ''
}

function typeOf(ext: string, fallback?: string): LocalFileItem['type'] {
  if (fallback === 'document' || fallback === 'image' || fallback === 'media' || fallback === 'other') {
    return fallback
  }
  if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp', 'svg'].includes(ext)) return 'image'
  if (['mp4', 'mov', 'avi', 'mkv', 'webm', 'mp3', 'wav', 'flac'].includes(ext)) return 'media'
  if (['doc', 'docx', 'pdf', 'ppt', 'pptx', 'xls', 'xlsx', 'txt', 'md', 'csv'].includes(ext)) return 'document'
  return 'other'
}

export const useFilesStore = defineStore('files', {
  state: () => ({
    items: [] as LocalFileItem[],
    loading: false,
    initialized: false
  }),

  getters: {
    totalBytes(state): number {
      return state.items.reduce((sum, f) => sum + (f.sizeBytes || 0), 0)
    }
  },

  actions: {
    async fetchCloudFiles(category?: string) {
      if (this.loading) return
      this.loading = true
      try {
        const res = await filesApi.listCloudFiles(category, 100)
        if (res.code === 200 && res.data) {
          this.items = res.data.map(f => {
            const title = f.title || f.fileName || '文件'
            const ext = extOf(title)
            return {
              id: String(f.id),
              title,
              size: f.fileSize != null ? formatFileSize(f.fileSize) : '',
              sizeBytes: f.fileSize ?? 0,
              time: formatTime(f.createTime),
              timeFull: formatTimeFull(f.createTime),
              type: typeOf(ext, f.category),
              sender: f.senderName || f.conversationName || '未知',
              fileUrl: f.fileUrl,
              conversationId: f.conversationId,
              conversationName: f.conversationName,
              ext
            }
          })
          this.initialized = true
        }
      } catch (e) {
        console.error('加载云端文件失败:', e)
      } finally {
        this.loading = false
      }
    },

    /**
     * 兼容旧调用：聊天侧仍可本地追加一条（随后以云端列表为准）
     */
    addFromChat(fileName: string, fileSize: string, sender: string, fileUrl?: string) {
      const ext = extOf(fileName)
      this.items.unshift({
        id: `local-${Date.now()}`,
        title: fileName,
        size: fileSize,
        sizeBytes: 0,
        time: '刚刚',
        timeFull: '刚刚',
        type: typeOf(ext),
        sender,
        fileUrl,
        ext
      })
    },

    remove(id: string) {
      this.items = this.items.filter(i => i.id !== id)
    },

    clear() {
      this.items = []
      this.initialized = false
    }
  }
})