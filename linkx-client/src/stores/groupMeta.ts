/**
 * 群元数据 Store
 * 按 sessionId 管理群公告、精华、成员、备注、群文件与群相册（懒加载默认值）
 */

// 从 Pinia 导入 defineStore
import { defineStore } from 'pinia'
// 导入群公告默认文案
import { GROUP_ANNOUNCEMENT_FULL, GROUP_ANNOUNCEMENT_SHORT } from '../data/groupDemo'

/** 群精华条目 */
export interface GroupEssenceItem {
  id: string                        // 精华 id
  user: string                      // 发布者
  date: string                      // 日期标签
  type: 'link' | 'video' | 'text'   // 内容类型
  content: string                   // 链接 URL 或文本内容
}

/** 群公告完整结构 */
export interface GroupAnnouncement {
  content: string  // 公告正文（可多行）
  author: string   // 发布者昵称
  role: string     // 角色（群主/管理员）
  time: string     // 发布时间标签
}

/** 群成员项 */
export interface GroupMember {
  id: string           // 成员 id
  name: string         // 显示名
  avatarText: string   // 文字头像
  avatarColor: string  // 头像背景色
  badge?: string       // 可选角标（群主/管理员）
}

/** 群共享文件项 */
export interface GroupFileItem {
  id: string       // 文件 id
  name: string     // 文件名
  size: string     // 大小
  user: string     // 上传者
  date: string     // 上传日期
  downloads: number // 下载次数
  fileUrl?: string  // 可选 URL
}

/** 群相册图片项 */
export interface GroupAlbumItem {
  id: string    // 图片 id
  url: string   // 图片 URL
  name: string  // 文件名或描述
  user: string  // 上传者
  time: string  // 上传时间标签
}

// 新群首次访问时的默认精华列表
const defaultEssence: GroupEssenceItem[] = [
  {
    id: 'e1',
    user: '有BB机的小豆包',
    date: '05-29',
    type: 'link',
    content: 'https://linkx.local/wiki/getting-started'
  },
  {
    id: 'e2',
    user: 'LinkX 团队',
    date: '05-08',
    type: 'text',
    content: '欢迎查阅群精华，重要链接会集中展示在这里。'
  }
]

// 默认群成员（演示）
const defaultMembers: GroupMember[] = [
  { id: 'm1', name: '有BB机的小豆包', avatarText: '有', avatarColor: '#f56c6c', badge: '群主' },
  { id: 'm2', name: '吱唔猪', avatarText: '吱', avatarColor: '#7cb342', badge: '管理员' },
  { id: 'm3', name: '清风', avatarText: '清', avatarColor: '#52c41a' },
  { id: 'm4', name: '晚香玉', avatarText: '晚', avatarColor: '#12b7f5' }
]

// 默认群文件（演示）
const defaultFiles: GroupFileItem[] = [
  {
    id: 'gf1',
    name: 'sub2api.2026-07-04_08-04-03.json',
    size: '58.4 KB',
    user: '蓬蒿人',
    date: '07/04',
    downloads: 12
  },
  {
    id: 'gf2',
    name: 'Cursor 账号3万+.txt',
    size: '15.7 MB',
    user: '打工人',
    date: '07/01',
    downloads: 120
  }
]

// 定义并导出 groupMeta Store
export const useGroupMetaStore = defineStore('groupMeta', {
  // 均以 sessionId 为键的 Record，按需懒初始化
  state: () => ({
    announcements: {} as Record<string, GroupAnnouncement>, // 群公告
    essence: {} as Record<string, GroupEssenceItem[]>,      // 群精华
    members: {} as Record<string, GroupMember[]>,         // 群成员
    remarks: {} as Record<string, string>,                // 群备注（用户自定义）
    files: {} as Record<string, GroupFileItem[]>,         // 群文件
    albums: {} as Record<string, GroupAlbumItem[]>        // 群相册
  }),

  actions: {
    /**
     * 获取某群的完整公告（不存在则用默认模板创建）
     * @param sessionId 群会话 id
     */
    announcementFor(sessionId: string): GroupAnnouncement {
      if (!this.announcements[sessionId]) {
        this.announcements[sessionId] = {
          content: GROUP_ANNOUNCEMENT_FULL,
          author: '群主',
          role: '群主',
          time: '昨天 20:27'
        }
      }
      return this.announcements[sessionId]
    },

    /**
     * 公告单行摘要（首行，最长 60 字）
     * @param sessionId 群会话 id
     */
    announcementShort(sessionId: string): string {
      const content = this.announcementFor(sessionId).content.trim()
      const firstLine = content.split('\n').find(l => l.trim()) || GROUP_ANNOUNCEMENT_SHORT
      return firstLine.length > 60 ? `${firstLine.slice(0, 60)}…` : firstLine
    },

    /**
     * 更新群公告正文
     * @param sessionId 群会话 id
     * @param content 新公告内容
     */
    updateAnnouncement(sessionId: string, content: string) {
      const cur = this.announcementFor(sessionId)
      cur.content = content
      cur.time = '刚刚'
    },

    /**
     * 获取群备注
     * @param sessionId 群会话 id
     */
    remarkFor(sessionId: string): string {
      return this.remarks[sessionId] ?? ''
    },

    /**
     * 设置群备注（trim 后存储）
     * @param sessionId 群会话 id
     * @param remark 备注文本
     */
    setRemark(sessionId: string, remark: string) {
      this.remarks[sessionId] = remark.trim()
    },

    /**
     * 获取群精华列表（懒加载默认数据）
     * @param sessionId 群会话 id
     */
    essenceFor(sessionId: string): GroupEssenceItem[] {
      if (!this.essence[sessionId]) {
        this.essence[sessionId] = [...defaultEssence]
      }
      return this.essence[sessionId]
    },

    /**
     * 新增精华条目（插入列表头部）
     * @param sessionId 群会话 id
     * @param item 精华内容（不含 id）
     */
    addEssence(sessionId: string, item: Omit<GroupEssenceItem, 'id'>) {
      const list = this.essenceFor(sessionId)
      list.unshift({ ...item, id: `e-${Date.now()}` })
    },

    /**
     * 获取群成员列表（懒加载默认成员）
     * @param sessionId 群会话 id
     */
    membersFor(sessionId: string): GroupMember[] {
      if (!this.members[sessionId]) {
        this.members[sessionId] = [...defaultMembers]
      }
      return this.members[sessionId]
    },

    /**
     * 批量添加群成员（按姓名去重）
     * @param sessionId 群会话 id
     * @param names 新成员姓名列表
     */
    addMembers(sessionId: string, names: string[]) {
      const list = this.membersFor(sessionId)
      for (const name of names) {
        if (list.some(m => m.name === name)) continue // 已存在则跳过
        list.push({
          id: `m-${Date.now()}-${Math.random().toString(36).slice(2, 5)}`,
          name,
          avatarText: name.charAt(0) || '?',
          avatarColor: '#12b7f5'
        })
      }
    },

    /**
     * 获取群文件列表（懒加载默认文件）
     * @param sessionId 群会话 id
     */
    filesFor(sessionId: string): GroupFileItem[] {
      if (!this.files[sessionId]) {
        this.files[sessionId] = [...defaultFiles]
      }
      return this.files[sessionId]
    },

    /**
     * 添加群文件记录
     * @param sessionId 群会话 id
     * @param file 文件信息（id/downloads/date 由 Store 填充）
     */
    addFile(
      sessionId: string,
      file: Omit<GroupFileItem, 'id' | 'downloads' | 'date'> & { date?: string }
    ) {
      const list = this.filesFor(sessionId)
      list.unshift({
        id: `gf-${Date.now()}`,
        downloads: 0,
        date: file.date ?? new Date().toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }),
        name: file.name,
        size: file.size,
        user: file.user,
        fileUrl: file.fileUrl
      })
    },

    /**
     * 获取群相册列表（懒加载空数组）
     * @param sessionId 群会话 id
     */
    albumFor(sessionId: string): GroupAlbumItem[] {
      if (!this.albums[sessionId]) {
        this.albums[sessionId] = []
      }
      return this.albums[sessionId]
    },

    /**
     * 批量添加群相册图片
     * @param sessionId 群会话 id
     * @param items 图片元数据列表
     */
    addAlbumImages(sessionId: string, items: { url: string; name: string; user: string }[]) {
      const list = this.albumFor(sessionId)
      const time = '刚刚'
      for (const item of items) {
        list.unshift({
          id: `ga-${Date.now()}-${Math.random().toString(36).slice(2, 5)}`,
          url: item.url,
          name: item.name,
          user: item.user,
          time
        })
      }
    }
  },

  // 持久化全部群元数据映射
  persist: {
    key: 'linkx-group-meta',
    paths: ['announcements', 'essence', 'members', 'remarks', 'files', 'albums']
  }
})
