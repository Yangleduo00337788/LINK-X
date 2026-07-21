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
  time: string
  type: 'document' | 'image' | 'media' | 'other'
  sender: string
  fileUrl?: string
  conversationName?: string
}

function formatTime(ts?: number): string {
  if (!ts) return ''
  try {
    return new Date(ts).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
  } catch {
    return ''
  }
}

export const useFilesStore = defineStore('files', {
  state: () => ({
    items: [] as LocalFileItem[],
    loading: false,
    initialized: false
  }),

  actions: {
    async fetchCloudFiles(category?: string) {
      if (this.loading) return
      this.loading = true
      try {
        const res = await filesApi.listCloudFiles(category, 100)
        if (res.code === 200 && res.data) {
          this.items = res.data.map(f => ({
            id: String(f.id),
            title: f.title || f.fileName || '文件',
            size: f.fileSize != null ? formatFileSize(f.fileSize) : '',
            time: formatTime(f.createTime),
            type: (f.category as LocalFileItem['type']) || 'other',
            sender: f.senderName || f.conversationName || '未知',
            fileUrl: f.fileUrl,
            conversationName: f.conversationName
          }))
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
      const ext = fileName.split('.').pop()?.toLowerCase() ?? ''
      let type: LocalFileItem['type'] = 'other'
      if (['png', 'jpg', 'jpeg', 'gif', 'webp'].includes(ext)) type = 'image'
      else if (['mp4', 'mov', 'avi'].includes(ext)) type = 'media'
      else if (['doc', 'docx', 'pdf', 'ppt', 'pptx', 'xls', 'xlsx'].includes(ext)) type = 'document'

      this.items.unshift({
        id: `local-${Date.now()}`,
        title: fileName,
        size: fileSize,
        time: '刚刚',
        type,
        sender,
        fileUrl
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
