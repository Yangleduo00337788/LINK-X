import { ref } from 'vue'

const isSettingsModalVisible = ref(false)
const settingsActiveTab = ref('general')

export function useSettings() {
  function openSettings(tab = 'general') {
    settingsActiveTab.value = tab
    isSettingsModalVisible.value = true
  }

  function closeSettings() {
    isSettingsModalVisible.value = false
  }

  return {
    isSettingsModalVisible,
    settingsActiveTab,
    openSettings,
    closeSettings
  }
}
