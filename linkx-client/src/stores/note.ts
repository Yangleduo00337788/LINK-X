/**
 * 笔记草稿 Store
 * 管理笔记标题与正文，并通过 localStorage 手动持久化
 */

// 从 Pinia 导入 defineStore
import { defineStore } from 'pinia'

// localStorage 中笔记草稿的存储键名
const NOTE_STORAGE_KEY = 'linkx-note-draft'

// 定义并导出 note Store
export const useNoteStore = defineStore('note', {
  // 初始状态
  state: () => ({
    title: '',   // 笔记标题
    content: ''  // 笔记正文（Markdown 或纯文本）
  }),

  actions: {
    /** 从 localStorage 加载已保存的草稿 */
    load() {
      try {
        // 读取本地存储的 JSON 字符串
        const raw = localStorage.getItem(NOTE_STORAGE_KEY)
        if (!raw) return // 无数据则保持默认空状态
        // 解析 JSON 并断言为标题/内容结构
        const data = JSON.parse(raw) as { title?: string; content?: string }
        this.title = data.title ?? ''     // 恢复标题，缺省为空串
        this.content = data.content ?? '' // 恢复正文，缺省为空串
      } catch {
        /* ignore */ // 解析失败时静默忽略，避免阻断应用
      }
    },

    /** 将当前标题与正文写入 localStorage */
    save() {
      localStorage.setItem(
        NOTE_STORAGE_KEY,
        // 序列化标题、内容及更新时间戳
        JSON.stringify({ title: this.title, content: this.content, updatedAt: Date.now() })
      )
    },

    /** 清空内存状态并删除 localStorage 中的草稿 */
    clear() {
      this.title = ''
      this.content = ''
      localStorage.removeItem(NOTE_STORAGE_KEY)
    }
  }
})
