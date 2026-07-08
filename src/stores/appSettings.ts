import { defineStore } from 'pinia'

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
    language: 'zh-CN'
  }),

  actions: {
    reset() {
      this.autoStart = false
      this.soundNotify = true
      this.messageDetail = true
      this.notifyAtMe = true
      this.notifySound = false
      this.privacyVerifyFriend = true
      this.privacyAllowStranger = false
      this.privacyShowOnline = true
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
      'language'
    ]
  }
})
