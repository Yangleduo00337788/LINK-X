import { defineStore } from 'pinia'
import { GROUP_ANNOUNCEMENT_FULL } from '../data/groupDemo'

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
  { id: 'm1', name: '有BB机的小豆包', avatarText: '有', avatarColor: '#f56c6c' },
  { id: 'm2', name: '吱唔猪', avatarText: '吱', avatarColor: '#7cb342' },
  { id: 'm3', name: '清风', avatarText: '清', avatarColor: '#52c41a' },
  { id: 'm4', name: '晚香玉', avatarText: '晚', avatarColor: '#12b7f5' }
]

export const useGroupMetaStore = defineStore('groupMeta', {
  state: () => ({
    announcements: {} as Record<string, GroupAnnouncement>,
    essence: {} as Record<string, GroupEssenceItem[]>,
    members: {} as Record<string, GroupMember[]>
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

    updateAnnouncement(sessionId: string, content: string) {
      const cur = this.announcementFor(sessionId)
      cur.content = content
      cur.time = '刚刚'
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
    }
  },

  persist: {
    key: 'linkx-group-meta',
    paths: ['announcements', 'essence', 'members']
  }
})
