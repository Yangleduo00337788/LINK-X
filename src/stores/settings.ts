import { defineStore } from 'pinia'

export const useSettingsStore = defineStore('settings', {
  state: () => ({
    isSettingsModalVisible: false,
    settingsActiveTab: 'general'
  }),

  actions: {
    openSettings(tab = 'general') {
      this.settingsActiveTab = tab
      this.isSettingsModalVisible = true
    },

    closeSettings() {
      this.isSettingsModalVisible = false
    }
  }
})
