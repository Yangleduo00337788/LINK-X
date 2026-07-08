import { defineStore } from 'pinia'
import type { ChatBackgroundId } from '../types'

export const useAppSettingsStore = defineStore('appSettings', {
  state: () => ({
    autoStart: false,
    soundNotify: true,
    messageDetail: true,
    notifyAtMe: true,
    notifySound: false,
    privacyVerifyFriend: true,
    privacyAllowStranger: false,
    privacyShowOnline: true,
    language: 'zh-CN',
    chatBackground: 'default' as ChatBackgroundId
  }),

  actions: {
    setChatBackground(id: ChatBackgroundId) {
      this.chatBackground = id
    },

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
