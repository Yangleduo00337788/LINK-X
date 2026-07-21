/**
 * 群元数据 Store
 * 按 sessionId 管理群公告、精华、成员、备注、群文件与群相册（对接真实后端）
 */

import { defineStore } from 'pinia'
import * as groupApi from '../api/group'
import * as groupAssetApi from '../api/groupAsset'
import * as groupAnnouncementApi from '../api/groupAnnouncement'
import type { GroupAnnouncementVO } from '../api/groupAnnouncement'
import { formatFileSize } from '../utils/file'
import { isDisplayableMediaUrl, normalizeMediaUrl } from '../utils/mediaUrl'

/** 群精华条目 */
export interface GroupEssenceItem {
  id: string
  user: string
  date: string
  type: 'link' | 'video' | 'text'
  content: string
}

/** 群公告条目（多条，可置顶） */
export interface GroupAnnouncementItem {
  id: string
  content: string
  author: string
  role: string
  time: string
  pinned: boolean
}

/** 群成员项 */
export interface GroupMember {
  id: string
  name: string
  avatarText: string
  avatarColor: string
  badge?: string
  avatarUrl?: string
  role?: 'owner' | 'admin' | 'member'
}

/** 群共享文件项 */
export interface GroupFileItem {
  id: string
  name: string
  size: string
  user: string
  date: string
  downloads: number
  fileUrl?: string
}

/** 群相册图片项 */
export interface GroupAlbumItem {
  id: string
  url: string
  name: string
  user: string
  time: string
}

function formatBytes(n?: number): string {
  if (n == null || Number.isNaN(n)) return '未知'
  return formatFileSize(n)
}

export const useGroupMetaStore = defineStore('groupMeta', {
  state: () => ({
    /** 公告列表（按会话） */
    announcements: {} as Record<string, GroupAnnouncementItem[]>,
    /** 侧栏/抽屉摘要：置顶优先，否则最新 */
    announcementDisplay: {} as Record<string, GroupAnnouncementItem | null>,
    essence: {} as Record<string, GroupEssenceItem[]>,
    members: {} as Record<string, GroupMember[]>,
    remarks: {} as Record<string, string>,
    files: {} as Record<string, GroupFileItem[]>,
    albums: {} as Record<string, GroupAlbumItem[]>,
    loading: {} as Record<string, boolean>
  }),

  actions: {
    async fetchMembers(sessionId: string, force = false) {
      if (this.loading[`members-${sessionId}`]) return
      const cached = this.members[sessionId]
      if (!force && cached) {
        const stale = cached.some(m => m.avatarUrl && !isDisplayableMediaUrl(m.avatarUrl))
        if (!stale) return
      }
      this.loading[`members-${sessionId}`] = true
      try {
        const res = await groupApi.listGroupMembers(sessionId)
        if (res.code === 200 && res.data) {
          this.members[sessionId] = res.data.map(m => ({
            id: String(m.userId),
            name: m.nickname || '用户',
            avatarText: (m.nickname || '用户').charAt(0),
            avatarColor: '#12b7f5',
            avatarUrl: normalizeMediaUrl(m.avatar) || undefined,
            role: m.role,
            badge: m.role === 'owner' ? '群主' : m.role === 'admin' ? '管理员' : undefined
          }))
          try {
            const { useAppStore } = await import('./app')
            const app = useAppStore()
            const session = app.sessions.find(s => s.id === sessionId && s.isGroup)
            if (session) {
              session.memberAvatars = this.members[sessionId].slice(0, 9).map(m => ({
                text: m.avatarText,
                color: m.avatarColor,
                imageUrl: m.avatarUrl
              }))
            }
          } catch {
            /* ignore */
          }
        }
      } catch (e) {
        console.error('加载群成员失败:', e)
      } finally {
        this.loading[`members-${sessionId}`] = false
      }
    },

    membersFor(sessionId: string): GroupMember[] {
      void this.fetchMembers(sessionId)
      return this.members[sessionId] || []
    },

    /** 加载群详情中的备注/群名，并刷新公告摘要 */
    async fetchAnnouncement(sessionId: string) {
      if (this.loading[`group-info-${sessionId}`]) return
      this.loading[`group-info-${sessionId}`] = true
      try {
        const res = await groupApi.getGroupInfo(sessionId)
        if (res.code === 200 && res.data) {
          if (res.data.myRemark != null) {
            this.remarks[sessionId] = res.data.myRemark
            try {
              const { useAppStore } = await import('./app')
              const app = useAppStore()
              const session = app.sessions.find(s => s.id === sessionId && s.isGroup)
              if (session) {
                const groupName = res.data.name || session.groupName || session.name
                session.groupName = groupName
                const remark = (res.data.myRemark || '').trim()
                session.groupRemark = remark || undefined
                session.name = remark || groupName
                session.avatarText = session.name.charAt(0) || '群'
              }
            } catch {
              /* ignore */
            }
          } else if (res.data.name) {
            try {
              const { useAppStore } = await import('./app')
              const app = useAppStore()
              const session = app.sessions.find(s => s.id === sessionId && s.isGroup)
              if (session) {
                session.groupName = res.data.name
                const remark = (session.groupRemark || this.remarks[sessionId] || '').trim()
                session.name = remark || res.data.name
              }
            } catch {
              /* ignore */
            }
          }
        }
      } catch (e) {
        console.error('加载群详情失败:', e)
      } finally {
        this.loading[`group-info-${sessionId}`] = false
      }
      void this.fetchAnnouncementDisplay(sessionId)
    },

    mapAnnouncement(a: GroupAnnouncementVO): GroupAnnouncementItem {
      const role =
        a.publisherRole === 'owner'
          ? '群主'
          : a.publisherRole === 'admin'
            ? '管理员'
            : a.publisherRole === 'member'
              ? '成员'
              : ''
      return {
        id: String(a.id),
        content: a.content || '',
        author: a.publisherNickname || '成员',
        role,
        time: a.updateTime || a.createTime || '',
        pinned: !!a.pinned
      }
    },

    async fetchAnnouncements(sessionId: string, force = false) {
      if (this.loading[`announcements-${sessionId}`]) return
      if (!force && this.announcements[sessionId]) return
      this.loading[`announcements-${sessionId}`] = true
      try {
        const res = await groupAnnouncementApi.listGroupAnnouncements(sessionId)
        if (res.code === 200 && res.data) {
          this.announcements[sessionId] = res.data.map(a => this.mapAnnouncement(a))
          this.refreshDisplayFromList(sessionId)
        }
      } catch (e) {
        console.error('加载群公告列表失败:', e)
      } finally {
        this.loading[`announcements-${sessionId}`] = false
      }
    },

    async fetchAnnouncementDisplay(sessionId: string) {
      if (this.loading[`announcement-display-${sessionId}`]) return
      this.loading[`announcement-display-${sessionId}`] = true
      try {
        const res = await groupAnnouncementApi.getDisplayAnnouncement(sessionId)
        if (res.code === 200) {
          this.announcementDisplay[sessionId] = res.data ? this.mapAnnouncement(res.data) : null
        }
      } catch (e) {
        console.error('加载公告摘要失败:', e)
      } finally {
        this.loading[`announcement-display-${sessionId}`] = false
      }
    },

    refreshDisplayFromList(sessionId: string) {
      const list = this.announcements[sessionId] || []
      const pinned = list.filter(a => a.pinned)
      if (pinned.length) {
        this.announcementDisplay[sessionId] = pinned[0]
        return
      }
      this.announcementDisplay[sessionId] = list[0] || null
    },

    announcementsFor(sessionId: string): GroupAnnouncementItem[] {
      if (!this.announcements[sessionId]) void this.fetchAnnouncements(sessionId)
      return this.announcements[sessionId] || []
    },

    announcementShort(sessionId: string): string {
      if (this.announcementDisplay[sessionId] === undefined) {
        void this.fetchAnnouncementDisplay(sessionId)
      }
      const content = (this.announcementDisplay[sessionId]?.content || '').trim()
      if (!content) return ''
      const firstLine = content.split('\n').find(l => l.trim()) || ''
      return firstLine.length > 60 ? `${firstLine.slice(0, 60)}…` : firstLine
    },

    async createAnnouncement(sessionId: string, content: string, pinned = false) {
      const res = await groupAnnouncementApi.createGroupAnnouncement(sessionId, {
        content,
        pinned
      })
      if (res.code === 200 && res.data) {
        if (!this.announcements[sessionId]) this.announcements[sessionId] = []
        const item = this.mapAnnouncement(res.data)
        if (item.pinned) {
          this.announcements[sessionId] = this.announcements[sessionId].map(a => ({
            ...a,
            pinned: false
          }))
        }
        this.announcements[sessionId].unshift(item)
        this.announcements[sessionId].sort((a, b) => Number(b.pinned) - Number(a.pinned))
        this.refreshDisplayFromList(sessionId)
        return true
      }
      return false
    },

    async setAnnouncementPinned(sessionId: string, announcementId: string, pinned: boolean) {
      const res = await groupAnnouncementApi.updateGroupAnnouncement(sessionId, announcementId, {
        pinned
      })
      if (res.code === 200 && res.data) {
        const list = this.announcements[sessionId] || []
        if (pinned) {
          for (const a of list) a.pinned = a.id === announcementId
        } else {
          const target = list.find(a => a.id === announcementId)
          if (target) target.pinned = false
        }
        list.sort((a, b) => Number(b.pinned) - Number(a.pinned))
        this.refreshDisplayFromList(sessionId)
        return true
      }
      return false
    },

    async updateAnnouncementContent(sessionId: string, announcementId: string, content: string) {
      const res = await groupAnnouncementApi.updateGroupAnnouncement(sessionId, announcementId, {
        content
      })
      if (res.code === 200 && res.data) {
        const list = this.announcements[sessionId] || []
        const idx = list.findIndex(a => a.id === announcementId)
        if (idx >= 0) list[idx] = this.mapAnnouncement(res.data)
        this.refreshDisplayFromList(sessionId)
        return true
      }
      return false
    },

    async removeAnnouncement(sessionId: string, announcementId: string) {
      const res = await groupAnnouncementApi.deleteGroupAnnouncement(sessionId, announcementId)
      if (res.code === 200) {
        const list = this.announcements[sessionId]
        if (list) {
          this.announcements[sessionId] = list.filter(a => a.id !== announcementId)
        }
        this.refreshDisplayFromList(sessionId)
        return true
      }
      return false
    },

    remarkFor(sessionId: string): string {
      if (this.remarks[sessionId] === undefined) {
        void this.fetchAnnouncement(sessionId)
      }
      return this.remarks[sessionId] ?? ''
    },

    async setRemark(sessionId: string, remark: string) {
      try {
        const res = await groupApi.updateGroupRemark(sessionId, remark.trim())
        if (res.code === 200) {
          const value = (res.data ?? remark).trim()
          this.remarks[sessionId] = value
          // 同步会话列表/顶栏显示名（有备注用备注，否则用真实群名）
          try {
            const { useAppStore } = await import('./app')
            const app = useAppStore()
            const session = app.sessions.find(s => s.id === sessionId && s.isGroup)
            if (session) {
              const groupName = session.groupName || session.name
              session.groupName = groupName
              session.groupRemark = value || undefined
              session.name = value || groupName
              session.avatarText = session.name.charAt(0) || '群'
            }
          } catch {
            /* ignore */
          }
          return true
        }
      } catch (e) {
        console.error('保存群备注失败:', e)
      }
      return false
    },

    async renameGroup(sessionId: string, name: string) {
      const trimmed = name.trim()
      if (!trimmed) return false
      try {
        const res = await groupApi.updateGroup(sessionId, { name: trimmed })
        if (res.code === 200 && res.data) {
          const newName = res.data.name || trimmed
          try {
            const { useAppStore } = await import('./app')
            const app = useAppStore()
            const session = app.sessions.find(s => s.id === sessionId && s.isGroup)
            if (session) {
              session.groupName = newName
              const remark = (this.remarks[sessionId] || session.groupRemark || '').trim()
              session.name = remark || newName
              session.avatarText = session.name.charAt(0) || '群'
              session.avatarColor = session.avatarColor || '#e74c3c'
            }
          } catch {
            /* ignore */
          }
          return true
        }
      } catch (e) {
        console.error('修改群名称失败:', e)
        throw e
      }
      return false
    },

    async addEssence(
      sessionId: string,
      item: Omit<GroupEssenceItem, 'id'> & { messageId?: string }
    ) {
      try {
        const mid = item.messageId != null && item.messageId !== '' ? Number(item.messageId) : undefined
        const res = await groupAssetApi.createGroupEssence(sessionId, {
          type: 'essence',
          title: item.user || '精华',
          content: item.content,
          messageId: mid != null && Number.isFinite(mid) ? mid : undefined
        })
        if (res.code === 200 && res.data) {
          if (!this.essence[sessionId]) this.essence[sessionId] = []
          this.essence[sessionId].unshift({
            id: String(res.data.id),
            user: item.user || res.data.uploaderNickname || '',
            date: (res.data.createTime || '').slice(0, 10),
            type: item.type || 'text',
            content: res.data.content || item.content
          })
          return true
        }
      } catch (e) {
        console.error('添加精华失败:', e)
        throw e
      }
      return false
    },

    async removeEssence(sessionId: string, essenceId: string) {
      try {
        const res = await groupAssetApi.deleteGroupAsset(sessionId, essenceId)
        if (res.code === 200) {
          const list = this.essence[sessionId]
          if (list) {
            this.essence[sessionId] = list.filter(e => e.id !== essenceId)
          }
          return true
        }
      } catch (e) {
        console.error('删除精华失败:', e)
        throw e
      }
      return false
    },

    addMembers(sessionId: string, members: GroupMember[]) {
      if (!this.members[sessionId]) this.members[sessionId] = []
      for (const m of members) {
        if (!this.members[sessionId].some(existing => existing.id === m.id)) {
          this.members[sessionId].push(m)
        }
      }
    },

    removeMember(sessionId: string, memberId: string) {
      if (this.members[sessionId]) {
        this.members[sessionId] = this.members[sessionId].filter(m => m.id !== memberId)
      }
    },

    async fetchFiles(sessionId: string) {
      if (this.loading[`files-${sessionId}`]) return
      this.loading[`files-${sessionId}`] = true
      try {
        const res = await groupAssetApi.listGroupAssets(sessionId, 'file')
        if (res.code === 200 && res.data) {
          this.files[sessionId] = res.data.map(a => ({
            id: String(a.id),
            name: a.fileName || a.title || '文件',
            size: formatBytes(a.fileSize),
            user: a.uploaderNickname || '成员',
            date: (a.createTime || '').slice(0, 10),
            downloads: a.downloadCount || 0,
            fileUrl: a.fileUrl
          }))
        }
      } catch (e) {
        console.error('加载群文件失败:', e)
      } finally {
        this.loading[`files-${sessionId}`] = false
      }
    },

    async fetchAlbum(sessionId: string) {
      if (this.loading[`album-${sessionId}`]) return
      this.loading[`album-${sessionId}`] = true
      try {
        const res = await groupAssetApi.listGroupAssets(sessionId, 'image')
        if (res.code === 200 && res.data) {
          this.albums[sessionId] = res.data.map(a => ({
            id: String(a.id),
            url: a.fileUrl || '',
            name: a.fileName || a.title || '',
            user: a.uploaderNickname || '成员',
            time: (a.createTime || '').slice(0, 10)
          }))
        }
      } catch (e) {
        console.error('加载群相册失败:', e)
      } finally {
        this.loading[`album-${sessionId}`] = false
      }
    },

    async fetchEssence(sessionId: string) {
      if (this.loading[`essence-${sessionId}`]) return
      this.loading[`essence-${sessionId}`] = true
      try {
        const res = await groupAssetApi.listGroupAssets(sessionId, 'essence')
        if (res.code === 200 && res.data) {
          this.essence[sessionId] = res.data.map(a => ({
            id: String(a.id),
            user: a.uploaderNickname || '成员',
            date: (a.createTime || '').slice(0, 10),
            type: 'link' as const,
            content: a.content || a.title || ''
          }))
        }
      } catch (e) {
        console.error('加载群精华失败:', e)
      } finally {
        this.loading[`essence-${sessionId}`] = false
      }
    },

    filesFor(sessionId: string): GroupFileItem[] {
      if (!this.files[sessionId]) void this.fetchFiles(sessionId)
      return this.files[sessionId] || []
    },

    albumFor(sessionId: string): GroupAlbumItem[] {
      if (!this.albums[sessionId]) void this.fetchAlbum(sessionId)
      return this.albums[sessionId] || []
    },

    essenceFor(sessionId: string): GroupEssenceItem[] {
      if (!this.essence[sessionId]) void this.fetchEssence(sessionId)
      return this.essence[sessionId] || []
    },

    async uploadFile(sessionId: string, file: File) {
      const res = await groupAssetApi.uploadGroupAsset(sessionId, 'file', file)
      if (res.code === 200 && res.data) {
        if (!this.files[sessionId]) this.files[sessionId] = []
        this.files[sessionId].unshift({
          id: String(res.data.id),
          name: res.data.fileName || file.name,
          size: formatBytes(res.data.fileSize ?? file.size),
          user: res.data.uploaderNickname || '我',
          date: (res.data.createTime || '').slice(0, 10) || '刚刚',
          downloads: 0,
          fileUrl: res.data.fileUrl
        })
        return true
      }
      return false
    },

    async uploadAlbumImages(sessionId: string, files: File[]) {
      let ok = 0
      if (!this.albums[sessionId]) this.albums[sessionId] = []
      for (const file of files) {
        try {
          const res = await groupAssetApi.uploadGroupAsset(sessionId, 'image', file)
          if (res.code === 200 && res.data) {
            this.albums[sessionId].unshift({
              id: String(res.data.id),
              url: res.data.fileUrl || '',
              name: res.data.fileName || file.name,
              user: res.data.uploaderNickname || '我',
              time: (res.data.createTime || '').slice(0, 10) || '刚刚'
            })
            ok += 1
          }
        } catch (e) {
          console.error('上传群相册失败:', e)
        }
      }
      return ok
    },

    clearForSession(sessionId: string) {
      delete this.announcements[sessionId]
      delete this.announcementDisplay[sessionId]
      delete this.essence[sessionId]
      delete this.members[sessionId]
      delete this.remarks[sessionId]
      delete this.files[sessionId]
      delete this.albums[sessionId]
    }
  }
})
