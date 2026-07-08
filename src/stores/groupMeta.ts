import { defineStore } from 'pinia'
import { GROUP_ANNOUNCEMENT_FULL, GROUP_ANNOUNCEMENT_SHORT } from '../data/groupDemo'

export interface GroupEssenceItem {
  id: string
  user: string
  date: string
  type: 'link' | 'video' | 'text'
  content: string
}

export interface GroupAnnouncement {
  content: string
  author: string
  role: string
  time: string
}

export interface GroupMember {
  id: string
  name: string
  avatarText: string
  avatarColor: string
  badge?: string
}

export interface GroupFileItem {
  id: string
  name: string
  size: string
  user: string
  date: string
  downloads: number
  fileUrl?: string
}

export interface GroupAlbumItem {
  id: string
  url: string
  name: string
  user: string
  time: string
}

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

const defaultMembers: GroupMember[] = [
  { id: 'm1', name: '有BB机的小豆包', avatarText: '有', avatarColor: '#f56c6c', badge: '群主' },
  { id: 'm2', name: '吱唔猪', avatarText: '吱', avatarColor: '#7cb342', badge: '管理员' },
  { id: 'm3', name: '清风', avatarText: '清', avatarColor: '#52c41a' },
  { id: 'm4', name: '晚香玉', avatarText: '晚', avatarColor: '#12b7f5' }
]

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

export const useGroupMetaStore = defineStore('groupMeta', {
  state: () => ({
    announcements: {} as Record<string, GroupAnnouncement>,
    essence: {} as Record<string, GroupEssenceItem[]>,
    members: {} as Record<string, GroupMember[]>,
    remarks: {} as Record<string, string>,
    files: {} as Record<string, GroupFileItem[]>,
    albums: {} as Record<string, GroupAlbumItem[]>
  }),

  actions: {
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

    announcementShort(sessionId: string): string {
      const content = this.announcementFor(sessionId).content.trim()
      const firstLine = content.split('\n').find(l => l.trim()) || GROUP_ANNOUNCEMENT_SHORT
      return firstLine.length > 60 ? `${firstLine.slice(0, 60)}…` : firstLine
    },

    updateAnnouncement(sessionId: string, content: string) {
      const cur = this.announcementFor(sessionId)
      cur.content = content
      cur.time = '刚刚'
    },

    remarkFor(sessionId: string): string {
      return this.remarks[sessionId] ?? ''
    },

    setRemark(sessionId: string, remark: string) {
      this.remarks[sessionId] = remark.trim()
    },

    essenceFor(sessionId: string): GroupEssenceItem[] {
      if (!this.essence[sessionId]) {
        this.essence[sessionId] = [...defaultEssence]
      }
      return this.essence[sessionId]
    },

    addEssence(sessionId: string, item: Omit<GroupEssenceItem, 'id'>) {
      const list = this.essenceFor(sessionId)
      list.unshift({ ...item, id: `e-${Date.now()}` })
    },

    membersFor(sessionId: string): GroupMember[] {
      if (!this.members[sessionId]) {
        this.members[sessionId] = [...defaultMembers]
      }
      return this.members[sessionId]
    },

    addMembers(sessionId: string, names: string[]) {
      const list = this.membersFor(sessionId)
      for (const name of names) {
        if (list.some(m => m.name === name)) continue
        list.push({
          id: `m-${Date.now()}-${Math.random().toString(36).slice(2, 5)}`,
          name,
          avatarText: name.charAt(0) || '?',
          avatarColor: '#12b7f5'
        })
      }
    },

    filesFor(sessionId: string): GroupFileItem[] {
      if (!this.files[sessionId]) {
        this.files[sessionId] = [...defaultFiles]
      }
      return this.files[sessionId]
    },

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

    albumFor(sessionId: string): GroupAlbumItem[] {
      if (!this.albums[sessionId]) {
        this.albums[sessionId] = []
      }
      return this.albums[sessionId]
    },

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

  persist: {
    key: 'linkx-group-meta',
    paths: ['announcements', 'essence', 'members', 'remarks', 'files', 'albums']
  }
})
