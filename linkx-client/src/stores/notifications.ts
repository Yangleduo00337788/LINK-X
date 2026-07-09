/**
 * 通知 Store
 * 管理好友请求与群邀请两类系统通知及其处理状态
 */

// 从 Pinia 导入 defineStore
import { defineStore } from 'pinia'

/** 好友相关通知（加好友、验证等） */
export interface FriendNotification {
  id: string                                              // 通知唯一 id
  avatar: string                                          // 申请人头像 URL
  name: string                                            // 申请人昵称
  action: string                                          // 动作描述文案
  date: string                                            // 通知日期
  message: string                                         // 附言或说明
  source: string                                          // 来源（如群名）
  status: '等待验证' | '已同意' | '已拒绝'                  // 处理状态
}

/** 群邀请通知 */
export interface GroupNotification {
  id: string                                              // 通知唯一 id
  groupName: string                                       // 目标群名称
  inviter: string                                         // 邀请人
  date: string                                            // 通知日期
  message: string                                         // 邀请说明
  status: '等待验证' | '已同意' | '已拒绝'                  // 处理状态
}

// mock 初始好友通知
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

// mock 初始群通知
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

// 定义并导出 notifications Store
export const useNotificationsStore = defineStore('notifications', {
  // 初始状态
  state: () => ({
    friendNotifs: [...initialFriendNotifs] as FriendNotification[], // 好友通知列表
    groupNotifs: [...initialGroupNotifs] as GroupNotification[]     // 群通知列表
  }),

  actions: {
    /**
     * 同意好友请求
     * @param id 通知 id
     * @returns 更新后的通知对象，未找到则 undefined
     */
    acceptFriend(id: string) {
      const n = this.friendNotifs.find(x => x.id === id)
      if (n) n.status = '已同意'
      return n
    },

    /**
     * 拒绝好友请求
     * @param id 通知 id
     */
    rejectFriend(id: string) {
      const n = this.friendNotifs.find(x => x.id === id)
      if (n) n.status = '已拒绝'
    },

    /**
     * 同意群邀请
     * @param id 通知 id
     * @returns 更新后的通知对象
     */
    acceptGroup(id: string) {
      const n = this.groupNotifs.find(x => x.id === id)
      if (n) n.status = '已同意'
      return n
    },

    /**
     * 拒绝群邀请
     * @param id 通知 id
     */
    rejectGroup(id: string) {
      const n = this.groupNotifs.find(x => x.id === id)
      if (n) n.status = '已拒绝'
    },

    /** 清空全部好友通知 */
    clearFriendNotifs() {
      this.friendNotifs = []
    },

    /** 清空全部群通知 */
    clearGroupNotifs() {
      this.groupNotifs = []
    }
  },

  // 持久化两类通知列表
  persist: {
    key: 'linkx-notifications',
    paths: ['friendNotifs', 'groupNotifs']
  }
})
