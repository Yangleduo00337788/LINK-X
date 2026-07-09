/**
 * 通讯录 Store
 * 管理联系人列表、好友搜索，以及与聊天会话的同步
 */

// 从 Pinia 导入 defineStore
import { defineStore } from 'pinia'
// 导入联系人项类型
import type { ContactItem } from '../types'
// 导入 mock 初始联系人数据
import { contacts as initialContacts } from '../data/mockData'

// 定义并导出 contacts Store
export const useContactsStore = defineStore('contacts', {
  // 初始状态
  state: () => ({
    items: [...initialContacts] as ContactItem[] // 联系人列表（浅拷贝 mock 数据）
  }),

  // 派生状态
  getters: {
    /** 筛选分组为「我的好友」的联系人 */
    friends(state): ContactItem[] {
      return state.items.filter(c => c.group === '我的好友')
    },

    /**
     * 按关键词搜索联系人（返回 getter 工厂函数）
     * @param keyword 搜索关键词，空则返回全部
     */
    searchUsers: state => (keyword: string) => {
      const q = keyword.trim().toLowerCase() // 规范化关键词：去空格、转小写
      if (!q) return state.items              // 无关键词时返回完整列表
      // 按姓名模糊匹配（不区分大小写）
      return state.items.filter(c => c.name.toLowerCase().includes(q))
    }
  },

  actions: {
    /**
     * 添加联系人（按 id 去重）
     * @param contact 待添加的联系人对象
     */
    addContact(contact: ContactItem) {
      if (this.items.some(c => c.id === contact.id)) return // 已存在则跳过
      this.items.push(contact)
    },

    /**
     * 按姓名快速添加好友
     * @param name 好友显示名
     * @returns 新联系人的 id
     */
    addByName(name: string) {
      const id = `contact-${Date.now()}` // 基于时间戳生成唯一 id
      this.addContact({
        id,
        name,
        avatarText: name.charAt(0) || '?', // 取首字作为头像文字
        avatarColor: '#12b7f5',            // 默认头像背景色
        group: '我的好友',                  // 归入好友分组
        online: true                       // 默认在线
      })
      return id
    },

    /**
     * 按 id 删除联系人
     * @param id 联系人 id
     */
    remove(id: string) {
      this.items = this.items.filter(c => c.id !== id)
    },

    /**
     * 从聊天会话同步好友到通讯录（若尚未存在）
     * 用于加群、加好友后保持通讯录与会话一致
     * @param session 会话摘要（含 id、名称、头像等）
     */
    syncFriendFromSession(session: { id: string; name: string; avatarText: string; avatarColor: string; online?: boolean }) {
      // 按 id 或姓名判断是否已存在
      const exists = this.items.find(c => c.id === session.id || c.name === session.name)
      if (exists) return
      this.addContact({
        id: session.id,
        name: session.name,
        avatarText: session.avatarText,
        avatarColor: session.avatarColor,
        group: '我的好友',
        online: session.online
      })
    }
  },

  // 持久化配置：仅持久化 items 字段
  persist: {
    key: 'linkx-contacts', // localStorage 键名
    paths: ['items']       // 持久化路径
  }
})
