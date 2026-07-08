import { defineStore } from 'pinia'

export interface FriendNotification {
  id: string
  avatar: string
  name: string
  action: string
  date: string
  message: string
  source: string
  status: '等待验证' | '已同意' | '已拒绝'
}

export interface GroupNotification {
  id: string
  groupName: string
  inviter: string
  date: string
  message: string
  status: '等待验证' | '已同意' | '已拒绝'
}

const initialFriendNotifs: FriendNotification[] = [
  {
    id: 'fn1',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=zling',
    name: 'Z铃ღ',
    action: '请求加为好友',
    date: '2026/06/07',
    message: '我是来自群聊的 Z铃ღ',
    source: '群聊-cursor分享群',
    status: '已同意'
  },
  {
    id: 'fn2',
    avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=ooo',
    name: 'o',
    action: '正在验证你的邀请',
    date: '2025/07/21',
    message: '请求添加对方为好友',
    source: '',
    status: '等待验证'
  }
]

const initialGroupNotifs: GroupNotification[] = [
  {
    id: 'gn1',
    groupName: '三角洲行动撞车沟通群',
    inviter: '清风',
    date: '2026/07/01',
    message: '邀请你加入群聊',
    status: '等待验证'
  }
]

export const useNotificationsStore = defineStore('notifications', {
  state: () => ({
    friendNotifs: [...initialFriendNotifs] as FriendNotification[],
    groupNotifs: [...initialGroupNotifs] as GroupNotification[]
  }),

  actions: {
    acceptFriend(id: string) {
      const n = this.friendNotifs.find(x => x.id === id)
      if (n) n.status = '已同意'
      return n
    },

    rejectFriend(id: string) {
      const n = this.friendNotifs.find(x => x.id === id)
      if (n) n.status = '已拒绝'
    },

    acceptGroup(id: string) {
      const n = this.groupNotifs.find(x => x.id === id)
      if (n) n.status = '已同意'
      return n
    },

    rejectGroup(id: string) {
      const n = this.groupNotifs.find(x => x.id === id)
      if (n) n.status = '已拒绝'
    },

    clearFriendNotifs() {
      this.friendNotifs = []
    },

    clearGroupNotifs() {
      this.groupNotifs = []
    }
  },

  persist: {
    key: 'linkx-notifications',
    paths: ['friendNotifs', 'groupNotifs']
  }
})
