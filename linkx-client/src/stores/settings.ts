/**
 * 设置页 Store
 * 管理设置页激活分类；设置以应用内整页展示（非弹窗）。
 */

import { defineStore } from 'pinia'
import type { SettingsTab } from '../types'
import { useAppStore } from './app'

export const useSettingsStore = defineStore('settings', {
  state: () => ({
    /** @deprecated 兼容旧调用；设置已改为整页，始终随 navKey 决定可见性 */
    isSettingsModalVisible: false,
    settingsActiveTab: 'account' as SettingsTab
  }),

  actions: {
    /**
     * 打开设置页并切换到指定分类
     * @param tab 要激活的分类，默认「我的账号」
     */
    openSettings(tab: SettingsTab | string = 'account') {
      const next = (tab || 'account') as SettingsTab
      this.settingsActiveTab = next
      this.isSettingsModalVisible = true
      const appStore = useAppStore()
      appStore.setNav('settings')
    },

    /** 离开设置页（切回消息） */
    closeSettings() {
      this.isSettingsModalVisible = false
      const appStore = useAppStore()
      if (appStore.navKey === 'settings') {
        appStore.setNav('chat')
      }
    },

    /** 仅切换分类（已在设置页内） */
    setTab(tab: SettingsTab) {
      this.settingsActiveTab = tab
    }
  }
})
