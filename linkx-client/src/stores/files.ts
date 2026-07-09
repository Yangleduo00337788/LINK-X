/**
 * 本地文件 Store
 * 管理从聊天等来源同步的文件列表及类型推断
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

// mock 初始文件列表（演示数据）
const initialFiles: LocalFileItem[] = [
  { id: 'f1', title: '产品需求文档_v2.docx', size: '1.2 MB', time: '10:30', type: 'document', sender: '张三' },
  { id: 'f2', title: 'Q3季度总结PPT.pptx', size: '4.5 MB', time: '昨天', type: 'document', sender: '李四' },
  { id: 'f3', title: '设计稿_切图.zip', size: '12.8 MB', time: '昨天', type: 'other', sender: '王五' },
  { id: 'f4', title: '会议录屏.mp4', size: '105.2 MB', time: '星期一', type: 'media', sender: '赵六' },
  { id: 'f5', title: 'UI视觉规范.png', size: '3.1 MB', time: '星期一', type: 'image', sender: '张三' }
]

// 定义并导出 files Store
export const useFilesStore = defineStore('files', {
  // 初始状态
  state: () => ({
    items: [...initialFiles] as LocalFileItem[]
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
