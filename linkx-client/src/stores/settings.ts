/**
 * 设置弹窗 Store
 * 管理设置模态框的显示状态与当前激活的标签页
 */

// 从 Pinia 导入 defineStore，用于创建响应式 Store
import { defineStore } from 'pinia'

// 定义并导出 settings Store
export const useSettingsStore = defineStore('settings', {
  // 初始状态
  state: () => ({
    isSettingsModalVisible: false, // 设置模态框是否可见
    settingsActiveTab: 'general'   // 当前激活的设置标签页 ID
  }),

  // 可变更状态的方法
  actions: {
    /**
     * 打开设置模态框
     * @param tab 要激活的标签页，默认为 'general'
     */
    openSettings(tab = 'general') {
      this.settingsActiveTab = tab           // 切换到指定标签
      this.isSettingsModalVisible = true     // 显示模态框
    },

    /** 关闭设置模态框 */
    closeSettings() {
      this.isSettingsModalVisible = false    // 隐藏模态框
    }
  }
})
