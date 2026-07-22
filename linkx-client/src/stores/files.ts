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

/** 单文件大小上限（用于过滤误写入的雪花 ID 等脏数据） */
const MAX_REASONABLE_FILE_BYTES = 50 * 1024 * 1024 * 1024

/** 把接口时间戳规范成毫秒；非法则返回 null */
function toEpochMs(ts?: number | string | null): number | null {
  if (ts == null || ts === '') return null
  const n = typeof ts === 'number' ? ts : Number(ts)
  if (!Number.isFinite(n) || n <= 0) return null
  // 10 位按秒，13 位按毫秒
  const ms = n < 1e12 ? Math.round(n * 1000) : Math.round(n)
  // JS Date 有效范围外（如误把雪花 ID 当时间）直接丢弃
  if (ms < 1e11 || ms > 1e14) return null
  const d = new Date(ms)
  if (Number.isNaN(d.getTime())) return null
  return ms
}

function normalizeFileSize(raw?: number | string | null): number {
  if (raw == null || raw === '') return 0
  const n = typeof raw === 'number' ? raw : Number(raw)
  if (!Number.isFinite(n) || n < 0 || n > MAX_REASONABLE_FILE_BYTES) return 0
  return Math.round(n)
}

function formatTime(ts?: number | string | null): string {
  const ms = toEpochMs(ts)
  if (ms == null) return ''
  return new Date(ms)
    .toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    })
    .replace(/\//g, '-')
}

function formatTimeFull(ts?: number | string | null): string {
  const ms = toEpochMs(ts)
  if (ms == null) return ''
  const d = new Date(ms)
  const date = d
    .toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
    .replace(/\//g, '-')
  const time = d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
  return `${date} ${time}`
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
      return state.items.reduce((sum, f) => sum + (Number(f.sizeBytes) || 0), 0)
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
            const sizeBytes = normalizeFileSize(f.fileSize as number | string | null | undefined)
            const convName = (f.conversationName || '').trim()
            return {
              id: String(f.id),
              title,
              size: sizeBytes > 0 ? formatFileSize(sizeBytes) : '',
              sizeBytes,
              time: formatTime(f.createTime as number | string | null | undefined),
              timeFull: formatTimeFull(f.createTime as number | string | null | undefined),
              type: typeOf(ext, f.category),
              sender: f.senderName || convName || '未知',
              fileUrl: f.fileUrl,
              conversationId: f.conversationId != null ? String(f.conversationId) : undefined,
              // 私聊会话常无群名：用发送者昵称作为文件夹名，避免显示「未分组」
              conversationName: convName || (f.senderName ? String(f.senderName) : undefined),
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