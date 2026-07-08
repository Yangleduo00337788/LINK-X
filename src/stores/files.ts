import { defineStore } from 'pinia'

export interface LocalFileItem {
  id: string
  title: string
  size: string
  time: string
  type: 'document' | 'image' | 'media' | 'other'
  sender: string
}

const initialFiles: LocalFileItem[] = [
  { id: 'f1', title: '产品需求文档_v2.docx', size: '1.2 MB', time: '10:30', type: 'document', sender: '张三' },
  { id: 'f2', title: 'Q3季度总结PPT.pptx', size: '4.5 MB', time: '昨天', type: 'document', sender: '李四' },
  { id: 'f3', title: '设计稿_切图.zip', size: '12.8 MB', time: '昨天', type: 'other', sender: '王五' },
  { id: 'f4', title: '会议录屏.mp4', size: '105.2 MB', time: '星期一', type: 'media', sender: '赵六' },
  { id: 'f5', title: 'UI视觉规范.png', size: '3.1 MB', time: '星期一', type: 'image', sender: '张三' }
]

export const useFilesStore = defineStore('files', {
  state: () => ({
    items: [...initialFiles] as LocalFileItem[]
  }),

  actions: {
    addFromChat(fileName: string, fileSize: string, sender: string) {
      const ext = fileName.split('.').pop()?.toLowerCase() ?? ''
      let type: LocalFileItem['type'] = 'other'
      if (['png', 'jpg', 'jpeg', 'gif', 'webp'].includes(ext)) type = 'image'
      else if (['mp4', 'mov', 'avi'].includes(ext)) type = 'media'
      else if (['doc', 'docx', 'pdf', 'ppt', 'pptx', 'xls', 'xlsx'].includes(ext)) type = 'document'

      this.items.unshift({
        id: `file-${Date.now()}`,
        title: fileName,
        size: fileSize,
        time: '刚刚',
        type,
        sender
      })
    },

    remove(id: string) {
      this.items = this.items.filter(f => f.id !== id)
    }
  },

  persist: {
    key: 'linkx-files',
    paths: ['items']
  }
})
