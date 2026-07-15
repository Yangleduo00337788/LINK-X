/**
 * 本地文件 Store
 * 管理从聊天消息中提取的文件列表
 * 文件数据从聊天消息中提取，无需单独后端 API
 */

// 从 Pinia 导入 defineStore
import { defineStore } from 'pinia'

/** 本地文件列表项结构 */
export interface LocalFileItem {
  id: string                                      // 唯一标识
  title: string                                   // 文件名/标题
  size: string                                    // 人类可读大小
  time: string                                    // 接收或保存时间标签
  type: 'document' | 'image' | 'media' | 'other' // 文件分类
  sender: string                                  // 发送者名称
  fileUrl?: string                                // 可选访问 URL
}

// 定义并导出 files Store
export const useFilesStore = defineStore('files', {
  // 初始状态
  state: () => ({
    items: [] as LocalFileItem[]  // 文件列表（从聊天消息中提取）
  }),

  actions: {
    /**
     * 从聊天消息添加文件到本地文件列表
     * @param fileName 文件名（含扩展名，用于推断类型）
     * @param fileSize 文件大小字符串
     * @param sender 发送者
     * @param fileUrl 可选文件 URL
     */
    addFromChat(fileName: string, fileSize: string, sender: string, fileUrl?: string) {
      const ext = fileName.split('.').pop()?.toLowerCase() ?? '' // 取扩展名
      let type: LocalFileItem['type'] = 'other' // 默认其他类型
      // 按扩展名分类
      if (['png', 'jpg', 'jpeg', 'gif', 'webp'].includes(ext)) type = 'image'
      else if (['mp4', 'mov', 'avi'].includes(ext)) type = 'media'
      else if (['doc', 'docx', 'pdf', 'ppt', 'pptx', 'xls', 'xlsx'].includes(ext)) type = 'document'

      // 插入列表头部
      this.items.unshift({
        id: `file-${Date.now()}`,
        title: fileName,
        size: fileSize,
        time: '刚刚',
        type,
        sender,
        fileUrl
      })
    },

    /**
     * 按 id 删除文件记录
     * @param id 文件项 id
     */
    remove(id: string) {
      this.items = this.items.filter(f => f.id !== id)
    }
  },

  // 持久化文件列表
  persist: {
    key: 'linkx-files',
    paths: ['items']
  }
})
