/**
 * 群元数据 Store
 * 按 sessionId 管理群公告、精华、成员、备注、群文件与群相册
 * 数据从后端 API 加载
 */

import { defineStore } from 'pinia'
import * as groupApi from '../api/group'
import * as noteApi from '../api/note'

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
     * 添加精华条目：调用 note API 写入一条 type=link 的笔记。
     */
    async addEssence(sessionId: string, item: Omit<GroupEssenceItem, 'id'>) {
      try {
        const res = await noteApi.createNote({
          title: item.user || '精华',
          content: item.content,
          type: 'link'
        })
        if (res.code === 200 && res.data) {
          if (!this.essence[sessionId]) {
            this.essence[sessionId] = []
          }
          this.essence[sessionId].unshift({
            id: String(res.data.id),
            user: item.user,
            date: (res.data.updateTime || res.data.createTime || '').slice(0, 10),
            type: 'link',
            content: item.content
          })
          return true
        }
      } catch (e) {
        console.error('添加精华失败:', e)
      }
      return false
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
     * 加载群共享文件列表：复用 note API，过滤 {@code type=file} 的笔记作为群文件。
     */
    async fetchFiles(sessionId: string) {
      if (this.loading[`files-${sessionId}`]) return
      this.loading[`files-${sessionId}`] = true
      try {
        const res = await noteApi.listNotes()
        if (res.code === 200 && res.data) {
          this.files[sessionId] = res.data
            .filter(n => (n.type || 'note') === 'file')
            .map(n => ({
              id: String(n.id),
              name: n.title || n.content.slice(0, 40) || '文件',
              size: '未知',
              user: '我',
              date: (n.updateTime || n.createTime || '').slice(0, 10),
              downloads: 0,
              fileUrl: n.content
            }))
        }
      } catch (e) {
        console.error('加载群文件失败:', e)
      } finally {
        this.loading[`files-${sessionId}`] = false
      }
    },

    /**
     * 加载群相册：复用 note API，过滤 {@code type=image} 的笔记。
     */
    async fetchAlbum(sessionId: string) {
      if (this.loading[`album-${sessionId}`]) return
      this.loading[`album-${sessionId}`] = true
      try {
        const res = await noteApi.listNotes()
        if (res.code === 200 && res.data) {
          this.albums[sessionId] = res.data
            .filter(n => (n.type || 'note') === 'image')
            .map(n => ({
              id: String(n.id),
              url: n.content,
              name: n.title || '',
              user: '我',
              time: (n.updateTime || n.createTime || '').slice(0, 10)
            }))
        }
      } catch (e) {
        console.error('加载群相册失败:', e)
      } finally {
        this.loading[`album-${sessionId}`] = false
      }
    },

    /**
     * 加载群精华：复用 note API，过滤 {@code type=link} 的笔记。
     */
    async fetchEssence(sessionId: string) {
      if (this.loading[`essence-${sessionId}`]) return
      this.loading[`essence-${sessionId}`] = true
      try {
        const res = await noteApi.listNotes()
        if (res.code === 200 && res.data) {
          this.essence[sessionId] = res.data
            .filter(n => (n.type || 'note') === 'link')
            .map(n => ({
              id: String(n.id),
              user: '我',
              date: (n.updateTime || n.createTime || '').slice(0, 10),
              type: 'link' as const,
              content: n.content
            }))
        }
      } catch (e) {
        console.error('加载群精华失败:', e)
      } finally {
        this.loading[`essence-${sessionId}`] = false
      }
    },

    /**
     * 懒加载获取群文件
     */
    filesFor(sessionId: string): GroupFileItem[] {
      if (!this.files[sessionId]) {
        void this.fetchFiles(sessionId)
      }
      return this.files[sessionId] || []
    },

    /**
     * 懒加载获取群相册
     */
    albumFor(sessionId: string): GroupAlbumItem[] {
      if (!this.albums[sessionId]) {
        void this.fetchAlbum(sessionId)
      }
      return this.albums[sessionId] || []
    },

    /**
     * 懒加载获取群精华
     */
    essenceFor(sessionId: string): GroupEssenceItem[] {
      if (!this.essence[sessionId]) {
        void this.fetchEssence(sessionId)
      }
      return this.essence[sessionId] || []
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
