/**
 * 应用偏好设置 Store
 * 管理通知、隐私、语言、聊天背景等用户可配置项
 */

// 从 Pinia 导入 defineStore
import { defineStore } from 'pinia'
// 导入聊天背景 ID 类型
import type { ChatBackgroundId } from '../types'

// 定义并导出 appSettings Store
export const useAppSettingsStore = defineStore('appSettings', {
  // 初始状态：各项设置的默认值
  state: () => ({
    autoStart: false,              // 开机自启动
    soundNotify: true,             // 消息声音提醒
    messageDetail: true,           // 通知中显示消息详情
    notifyAtMe: true,              // @我 时特别提醒
    notifySound: false,            // 通知提示音（与 soundNotify 区分）
    privacyVerifyFriend: true,     // 加好友需验证
    privacyAllowStranger: false,   // 允许陌生人查看资料
    privacyShowOnline: true,       // 显示在线状态
    language: 'zh-CN',             // 界面语言
    chatBackground: 'default' as ChatBackgroundId // 聊天背景主题
  }),

  actions: {
    /**
     * 设置聊天背景
     * @param id 背景主题 ID
     */
    setChatBackground(id: ChatBackgroundId) {
      this.chatBackground = id
    },

    /** 将所有设置恢复为默认值 */
    reset() {
      this.autoStart = false
      this.soundNotify = true
      this.messageDetail = true
      this.notifyAtMe = true
      this.notifySound = false
      this.privacyVerifyFriend = true
      this.privacyAllowStranger = false
      this.privacyShowOnline = true
      this.chatBackground = 'default'
    }
  },

  // 持久化全部可配置字段
  persist: {
    key: 'linkx-settings',
    paths: [
      'autoStart',
      'soundNotify',
      'messageDetail',
      'notifyAtMe',
      'notifySound',
      'privacyVerifyFriend',
      'privacyAllowStranger',
      'privacyShowOnline',
      'language',
      'chatBackground'
    ]
  }
})
