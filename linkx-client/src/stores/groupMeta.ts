/**
 * 群元数据 Store
 * 按 sessionId 管理群公告、精华、成员、备注、群文件与群相册（对接真实后端）
 */

import { defineStore } from 'pinia'
import * as groupApi from '../api/group'
import * as groupAssetApi from '../api/groupAsset'
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

/** 群公告完整结构 */
export interface GroupAnnouncement {
  content: string
  author: string
  role: string
  time: string
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
    announcements: {} as Record<string, GroupAnnouncement>,
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
        // 缓存了未签发的 object key / 无效地址时强制刷新
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
          // 同步会话列表拼图头像
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
          if (res.data.myRemark != null) {
            this.remarks[sessionId] = res.data.myRemark
          }
        }
      } catch (e) {
        console.error('加载群公告失败:', e)
      } finally {
        this.loading[`announcement-${sessionId}`] = false
      }
    },

    announcementFor(sessionId: string): GroupAnnouncement {
      if (!this.announcements[sessionId]) {
        void this.fetchAnnouncement(sessionId)
      }
      return this.announcements[sessionId] || { content: '', author: '', role: '', time: '' }
    },

    announcementShort(sessionId: string): string {
      const content = this.announcementFor(sessionId).content.trim()
      if (!content) return ''
      const firstLine = content.split('\n').find(l => l.trim()) || ''
      return firstLine.length > 60 ? `${firstLine.slice(0, 60)}…` : firstLine
    },

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
          this.remarks[sessionId] = (res.data ?? remark).trim()
          return true
        }
      } catch (e) {
        console.error('保存群备注失败:', e)
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
      delete this.essence[sessionId]
      delete this.members[sessionId]
      delete this.remarks[sessionId]
      delete this.files[sessionId]
      delete this.albums[sessionId]
    }
  }
})
