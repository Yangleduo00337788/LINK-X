/**
 * 群元数据 Store
 * 按 sessionId 管理群公告、精华、成员、备注、群文件与群相册
 * 数据从后端 API 加载
 */

import { defineStore } from 'pinia'
import * as groupApi from '../api/group'

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
  avatarUrl?: string   // 头像 URL
  role?: 'owner' | 'admin' | 'member'
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

// 定义并导出 groupMeta Store
export const useGroupMetaStore = defineStore('groupMeta', {
  state: () => ({
    announcements: {} as Record<string, GroupAnnouncement>, // 群公告
    essence: {} as Record<string, GroupEssenceItem[]>,      // 群精华
    members: {} as Record<string, GroupMember[]>,         // 群成员
    remarks: {} as Record<string, string>,                // 群备注（用户自定义）
    files: {} as Record<string, GroupFileItem[]>,         // 群文件
    albums: {} as Record<string, GroupAlbumItem[]>,        // 群相册
    loading: {} as Record<string, boolean>                 // 各群数据加载状态
  }),

  actions: {
    /**
     * 加载群成员列表
     */
    async fetchMembers(sessionId: string) {
      if (this.loading[`members-${sessionId}`]) return
      this.loading[`members-${sessionId}`] = true
      try {
        const res = await groupApi.listGroupMembers(sessionId)
        if (res.code === 200 && res.data) {
          this.members[sessionId] = res.data.map(m => ({
            id: String(m.userId),
            name: m.nickname || '用户',
            avatarText: (m.nickname || '用户').charAt(0),
            avatarColor: '#12b7f5',
            avatarUrl: m.avatar,
            role: m.role,
            badge: m.role === 'owner' ? '群主' : m.role === 'admin' ? '管理员' : undefined
          }))
        }
      } catch (e) {
        console.error('加载群成员失败:', e)
      } finally {
        this.loading[`members-${sessionId}`] = false
      }
    },

    /**
     * 获取群成员列表（懒加载）
     */
    membersFor(sessionId: string): GroupMember[] {
      if (!this.members[sessionId]) {
        this.fetchMembers(sessionId)
      }
      return this.members[sessionId] || []
    },

    /**
     * 获取某群的完整公告
     */
    async fetchAnnouncement(sessionId: string) {
      if (this.loading[`announcement-${sessionId}`]) return
      this.loading[`announcement-${sessionId}`] = true
      try {
        const res = await groupApi.getGroupInfo(sessionId)
        if (res.code === 200 && res.data) {
          this.announcements[sessionId] = {
            content: res.data.announcement || '',
            author: res.data.ownerNickname || '群主',
            role: '群主',
            time: ''
          }
        }
      } catch (e) {
        console.error('加载群公告失败:', e)
      } finally {
        this.loading[`announcement-${sessionId}`] = false
      }
    },

    /**
     * 获取群公告（懒加载）
     */
    announcementFor(sessionId: string): GroupAnnouncement {
      if (!this.announcements[sessionId]) {
        this.fetchAnnouncement(sessionId)
        return { content: '', author: '', role: '', time: '' }
      }
      return this.announcements[sessionId]
    },

    /**
     * 公告单行摘要（首行，最长 60 字）
     */
    announcementShort(sessionId: string): string {
      const content = this.announcementFor(sessionId).content.trim()
      if (!content) return ''
      const firstLine = content.split('\n').find(l => l.trim()) || ''
      return firstLine.length > 60 ? `${firstLine.slice(0, 60)}…` : firstLine
    },

    /**
     * 更新群公告
     */
    async updateAnnouncement(sessionId: string, content: string) {
      try {
        const res = await groupApi.updateGroup(sessionId, { announcement: content })
        if (res.code === 200 && res.data) {
          this.announcements[sessionId] = {
            content: res.data.announcement || content,
            author: res.data.ownerNickname || '群主',
            role: '群主',
            time: '刚刚'
          }
          return true
        }
      } catch (e) {
        console.error('更新群公告失败:', e)
      }
      return false
    },

    /**
     * 获取群备注
     */
    remarkFor(sessionId: string): string {
      return this.remarks[sessionId] ?? ''
    },

    /**
     * 设置群备注（本地存储）
     */
    setRemark(sessionId: string, remark: string) {
      this.remarks[sessionId] = remark.trim()
    },

    /**
     * 获取群精华列表
     */
    essenceFor(sessionId: string): GroupEssenceItem[] {
      return this.essence[sessionId] || []
    },

    /**
     * 添加精华条目（需后端支持）
     */
    addEssence(sessionId: string, item: Omit<GroupEssenceItem, 'id'>) {
      if (!this.essence[sessionId]) {
        this.essence[sessionId] = []
      }
      this.essence[sessionId].unshift({ ...item, id: `e-${Date.now()}` })
    },

    /**
     * 添加群成员（本地更新）
     */
    addMembers(sessionId: string, members: GroupMember[]) {
      if (!this.members[sessionId]) {
        this.members[sessionId] = []
      }
      for (const m of members) {
        if (!this.members[sessionId].some(existing => existing.id === m.id)) {
          this.members[sessionId].push(m)
        }
      }
    },

    /**
     * 移除群成员（本地更新）
     */
    removeMember(sessionId: string, memberId: string) {
      if (this.members[sessionId]) {
        this.members[sessionId] = this.members[sessionId].filter(m => m.id !== memberId)
      }
    },

    /**
     * 获取群文件列表
     */
    filesFor(sessionId: string): GroupFileItem[] {
      return this.files[sessionId] || []
    },

    /**
     * 添加群文件记录
     */
    addFile(sessionId: string, file: Omit<GroupFileItem, 'id' | 'downloads' | 'date'> & { date?: string }) {
      if (!this.files[sessionId]) {
        this.files[sessionId] = []
      }
      this.files[sessionId].unshift({
        id: `gf-${Date.now()}`,
        downloads: 0,
        date: file.date ?? new Date().toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }),
        ...file
      })
    },

    /**
     * 获取群相册列表
     */
    albumFor(sessionId: string): GroupAlbumItem[] {
      return this.albums[sessionId] || []
    },

    /**
     * 批量添加群相册图片
     */
    addAlbumImages(sessionId: string, items: { url: string; name: string; user: string }[]) {
      if (!this.albums[sessionId]) {
        this.albums[sessionId] = []
      }
      const time = '刚刚'
      for (const item of items) {
        this.albums[sessionId].unshift({
          id: `ga-${Date.now()}-${Math.random().toString(36).slice(2, 5)}`,
          ...item,
          time
        })
      }
    },

    /**
     * 清除某群的元数据
     */
    clearForSession(sessionId: string) {
      delete this.announcements[sessionId]
      delete this.essence[sessionId]
      delete this.members[sessionId]
      delete this.remarks[sessionId]
      delete this.files[sessionId]
      delete this.albums[sessionId]
    }
  },

  persist: {
    key: 'linkx-group-meta',
    paths: ['remarks'] // 仅持久化群备注
  }
})
